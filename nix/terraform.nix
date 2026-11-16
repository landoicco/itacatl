# HashiCorp's own release binary. nixpkgs' terraform is unfree (BSL), so the public cache never
# builds it and every machine would compile it from source. To upgrade, change the version and
# copy the hashes from https://releases.hashicorp.com/terraform/<version>/terraform_<version>_SHA256SUMS.
{ pkgs, system }:
let
  version = "1.17.1";
  platforms = {
    x86_64-linux = { arch = "linux_amd64"; sha256 = "4638628058e71a55212b1c563712eac03b902128f40740f3dc8174df12135061"; };
    aarch64-linux = { arch = "linux_arm64"; sha256 = "988e950a1c23058093b0e66ab2d5fb53fc18345ecfbd5871acf51dbac9325edd"; };
    x86_64-darwin = { arch = "darwin_amd64"; sha256 = "fde92387cf9c0de74255003c85291f8fbede54585c10d0635ca7a43633d4d171"; };
    aarch64-darwin = { arch = "darwin_arm64"; sha256 = "b1ecb3fe0d0a0290c1ab6f83d967d43f25da05d0b671ee0fb355252268dce95a"; };
  };
  platform = platforms.${system};
in
pkgs.stdenvNoCC.mkDerivation {
  pname = "terraform";
  inherit version;
  src = pkgs.fetchurl {
    url = "https://releases.hashicorp.com/terraform/${version}/terraform_${version}_${platform.arch}.zip";
    inherit (platform) sha256;
  };
  nativeBuildInputs = [ pkgs.unzip ];
  sourceRoot = ".";
  installPhase = "install -Dm755 terraform $out/bin/terraform";
}
