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
        ];

        # The project's tools. Add new ones to the list of the part that needs them, or here
        tools = backendTools ++ frontendTools ++ infraTools ++ [
          pkgs.gh        # PRs and CI from the terminal; credential helper for git push
        ];
      in
      {
        # nix develop .#backend, .#frontend, .#infra (the CI jobs)
        devShells.backend = pkgs.mkShell {
          packages = backendTools;
        };
        devShells.frontend = pkgs.mkShell {
          packages = frontendTools;
        };
        devShells.infra = pkgs.mkShell {
          packages = infraTools;
        };

        # nix develop
        devShells.default = pkgs.mkShell {
          packages = tools;

          # Versions come from the Nix packages, so the banner costs no process startups
          shellHook = ''
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
            echo ""
          '';
        };
      }
    );
}
