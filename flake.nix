{
  description = "Development environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  # AI assistants are deliberately left out: each developer brings their own, or none.
  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          # Terraform's license (BSL) is unfree; nothing else is allowed in by accident
          config.allowUnfreePredicate = pkg: nixpkgs.lib.getName pkg == "terraform";
        };
        terraform = import ./nix/terraform.nix { inherit pkgs system; };

        # Each part of the repo also gets its own shell, so a CI job downloads only what it needs
        backendTools = with pkgs; [
          jdk21_headless # backend build and Spotless
          maven
        ];
        frontendTools = with pkgs; [
          nodejs_22      # frontend build, lint and Prettier
        ];
        infraTools = [
          terraform      # HashiCorp's binary (nix/terraform.nix)
          pkgs.tflint    # Terraform linter
          pkgs.terraform-docs # inputs and outputs tables in each module's README
          pkgs.trivy     # misconfiguration scan of the Terraform code
        ];

        # Every directory that runs terraform init would download its own copy of the AWS
        # provider (~770 MB unpacked). One cache shared by all of them keeps a single copy, and
        # init links to it; the lock files still verify its checksums
        terraformEnv = ''
          export TF_PLUGIN_CACHE_DIR="''${XDG_CACHE_HOME:-$HOME/.cache}/terraform/plugins"
          mkdir -p "$TF_PLUGIN_CACHE_DIR"
        '';

        # The project's tools. Add new ones to the list of the part that needs them, or here
        tools = backendTools ++ frontendTools ++ infraTools ++ [
          pkgs.gh        # PRs and CI from the terminal; credential helper for git push
        ];
      in
      {
        # nix develop .#backend, .#frontend, .#infra (the CI jobs). NoCC: no step compiles C, and
        # stdenv's compiler and binutils would add hundreds of MB to every job's download
        devShells.backend = pkgs.mkShellNoCC {
          packages = backendTools;
        };
        devShells.frontend = pkgs.mkShellNoCC {
          packages = frontendTools;
        };
        devShells.infra = pkgs.mkShellNoCC {
          packages = infraTools;
          shellHook = terraformEnv;
        };

        # nix develop
        devShells.default = pkgs.mkShell {
          packages = tools;

          # Versions come from the Nix packages, so the banner costs no process startups
          shellHook = terraformEnv + ''
            echo ""
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            printf '  🛠️  \033[1mItacatl dev tools\033[0m\n'
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            echo "   ☕ Java       ${pkgs.jdk21_headless.version}"
            echo "   📦 Maven      ${pkgs.maven.version}"
            echo "   🟢 Node.js    ${pkgs.nodejs_22.version}"
            echo "   🐙 gh         ${pkgs.gh.version}"
            echo "   🏗️  Terraform  ${terraform.version}"
            echo "   🧹 TFLint     ${pkgs.tflint.version}"
            echo "   📝 tf-docs    ${pkgs.terraform-docs.version}"
            echo "   🔍 Trivy      ${pkgs.trivy.version}"
            echo ""
          '';
        };
      }
    );
}
