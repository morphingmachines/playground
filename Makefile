MILL = ${PWD}/mill


REQUIRED_FIRTOOL_VERSION := CIRCT firtool-1.62.1
FIRTOOL_OK := $(shell firtool --version 2>/dev/null | grep -Fxq "$(REQUIRED_FIRTOOL_VERSION)" && echo yes || echo no)

bsp:
	$(MILL) -i mill.bsp.BSP/install

compile:
	$(MILL) -i -j 0 __.compile

console:
	$(MILL) -i -j 0 playground.console

rtl: check-firtool
	$(MILL) -i -j 0 playground.verilog

rocketchip-scaladoc:
	$(MILL) -i -j 0 myrocketchip.docJar

.PHONY: clean
clean:   ## Clean all generated files
	$(MILL) clean
	@rm -rf out


.PHONY: check-firtool
check-firtool: ## Check for correct version of fir-tool
ifeq ($(FIRTOOL_OK),no)
	$(error "Error: Expected firtool version: $(REQUIRED_FIRTOOL_VERSION) not found")
endif
