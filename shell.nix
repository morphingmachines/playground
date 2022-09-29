with import (fetchTarball {
  url    = "https://github.com/NixOS/nixpkgs/archive/b3adce1542df29dd5a35077a3d42bcc47100e74e.tar.gz";
  sha256 = "0356xngf3wvmi7qdzdwvxb87lnwmlkn3bsds3qqn3gdxhigs6mgc";
}) {
  config = {
    packageOverrides = pkgs: with pkgs; {
      llvmPackages = llvmPackages_14;
      clang = clang_14;
      lld = lld_14;
      jdk = if stdenv.isDarwin then jdk11_headless else graalvm11-ce;
      python = python39Full;
      pythonPexpect = python39Packages.pexpect;
    };
  };
};

let
  clang-multiple-target =
    pkgs.writeScriptBin "clang" ''
      #!${pkgs.bash}/bin/bash
      if [[ "$*" == *--target=riscv64* || "$*" == *-target\ riscv64* ]]; then
        # works partially, namely no ld
        ${pkgs.pkgsCross.riscv64.buildPackages.clang}/bin/riscv64-unknown-linux-gnu-clang \
          --target=riscv64 \
          $@
      else
        # works fully
        ${pkgs.clang}/bin/clang $@
      fi
    '';
  clangpp-multiple-target =
    pkgs.writeScriptBin "clang++" ''
      #!${pkgs.bash}/bin/bash
      if [[ "$*" == *--target=riscv64* || "$*" == *-target\ riscv64* ]]; then
        # works partially, namely no ld
        ${pkgs.pkgsCross.riscv64.buildPackages.clang}/bin/riscv64-unknown-linux-gnu-clang++ \
          --target=riscv64 \
          $@
      else
        # works fully
        ${pkgs.clang}/bin/clang++ $@
      fi
    '';
  cpp-multiple-target = pkgs.writeScriptBin "cpp" ''
    #!${pkgs.bash}/bin/bash
    ${pkgs.clang}/bin/cpp $@
  '';
  cc = if stdenv.isDarwin then [clang] else [
    clang-multiple-target
    clangpp-multiple-target
    cpp-multiple-target
  ];
in pkgs.callPackage (
  {
    mkShellNoCC,
    jdk,
    python, pythonPexpect,
    gnumake, git, mill, wget, parallel, dtc, protobuf, antlr4,
    llvmPackages, clang, lld, verilator, cmake, ninja, rcs,
    autoconf, automake, openocd
  }:

  mkShellNoCC {
    name = "sequencer-playground";
    depsBuildBuild = [
      jdk gnumake git mill wget parallel dtc protobuf antlr4
      verilator cmake ninja rcs autoconf automake openocd
      llvmPackages.llvm lld
      python pythonPexpect
      cc
      pkgs.pkgsCross.riscv64-embedded.buildPackages.gdb
    ];
  }
) {}
