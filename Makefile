MILL = ${PWD}/mill

bsp:
	$(MILL) -i mill.bsp.BSP/install

compile:
	$(MILL) -i -j 0 __.compile

rtl:
	$(MILL) -i -j 0 playground.verilog

rocketchip-scaladoc:
	$(MILL) -i -j 0 myrocketchip.docJar

clean:
	git clean -fd
