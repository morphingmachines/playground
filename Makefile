project = playground

MILL = ${PWD}/mill

# Shared targets and Chisel version / firtool handling, same as consumer repos
-include ./Makefile.include

bsp: ## Generate BSP config for IDEs
	$(MILL) -i mill.bsp.BSP/install

compile: ## Compile all modules
	$(MILL) -i -j 0 __.compile

rtl: check-firtool ## Elaborate playground's top and run firtool
	$(MILL) -i -j 0 playground.verilog

rocketchip-scaladoc: ## Generate rocket-chip Scala API documentation
	$(MILL) -i -j 0 myrocketchip.docJar
