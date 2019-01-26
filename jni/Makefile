JAVA_HOME=/usr/lib/jvm/jdk1.8.0_181


x86: rings.o wsd_packet.o exb_packet.o ru_serbis_okto_node_hardware_NativeApi__x86.o
	g++ rings.o wsd_packet.o exb_packet.o ru_serbis_okto_node_hardware_NativeApi__x86.o -shared -O3 -fPIC -o libHw_x86.so

raspb3: rings.o wsd_packet.o exb_packet.o ru_serbis_okto_node_hardware_NativeApi__raspb3.o
	g++ rings.o wsd_packet.o exb_packet.o ru_serbis_okto_node_hardware_NativeApi__raspb3.o -shared -O3 -fPIC -o libHw.so


ru_serbis_okto_node_hardware_NativeApi__x86.o: ru_serbis_okto_node_hardware_NativeApi__x86.cpp
	g++ -c ru_serbis_okto_node_hardware_NativeApi__x86.cpp -I/usr/include -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux -fPIC

ru_serbis_okto_node_hardware_NativeApi__raspb3.o: ru_serbis_okto_node_hardware_NativeApi__raspb3.cpp
	g++ -c ru_serbis_okto_node_hardware_NativeApi__raspb3.cpp -I/usr/include -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux -fPIC

rings.o: rings.c
	g++ -c rings.c -fPIC

wsd_packet.o: wsd_packet.c
	g++ -c wsd_packet.c -fPIC

exb_packet.o: exb_packet.c
	g++ -c exb_packet.c -fPIC

clean: 
	rm *.o

