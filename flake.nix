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
        pkgs = nixpkgs.legacyPackages.${system};

        # The project's tools. Add new ones here, e.g. with pkgs; [ nodejs python3 ]
        tools = with pkgs; [
          jdk21_headless # backend build and Spotless
          maven
          nodejs_22      # frontend build, lint and Prettier
          jq             # reads the payload in .claude/hooks/format.sh
          gh             # PRs and CI from the terminal; credential helper for git push
        ];
      in
      {
        # nix develop
        devShells.default = pkgs.mkShell {
          packages = tools;

          # Versions come from the Nix packages, so the banner costs no process startups
          shellHook = ''
            echo ""
            echo "🛠️  itacatl dev toolkit"
            echo "   ☕ Java     ${pkgs.jdk21_headless.version}"
            echo "   📦 Maven    ${pkgs.maven.version}"
            echo "   🟢 Node.js  ${pkgs.nodejs_22.version}"
            echo "   🔍 jq       ${pkgs.jq.version}"
            echo "   🐙 gh       ${pkgs.gh.version}"
            echo ""
          '';
        };
      }
    );
}
