/* NATIVE API LIBRARY FOR RASPBERRY PI 3 */
#include "ru_serbis_okto_node_hardware_NativeApi__raspb3.h"
#include "rings.h"
#include "wsd_packet.h"
#include "exb_packet.h"
#include <iostream>
#include <stdint.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <malloc.h>
#include <sys/socket.h>
#include <stdio.h>
#include <sys/un.h>
#include <stdlib.h>
#include <stdbool.h>
#include <termios.h>

extern "C" {
#include <time.h>
}

#define MODE_PREAMBLE 0
#define MODE_HEADER 1
#define MODE_BODY 2

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialOpen
 * Signature: ([BI)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialOpen
  (JNIEnv* env, jobject, jbyteArray device, jint baud) {

  jbyte* buf = env->GetByteArrayElements(device, 0);
  jsize devSize = env->GetArrayLength(device) + 1;
  char *dev = (char*) malloc(devSize);
  memcpy(dev, buf, devSize - 1);
  dev[devSize - 1] = 0;


  int uart0_filestream = open((const char*) dev, O_RDWR | O_NOCTTY/* | O_NDELAY | O_NONBLOCK*/);

  free(dev);

  if (uart0_filestream == -1) {
    env->ReleaseByteArrayElements(device, buf, JNI_ABORT); 
    return (jint) -1;
  }

  struct termios options;
  tcgetattr(uart0_filestream, &options);
  options.c_cflag = B115200 | CS8 | CLOCAL | CREAD;		//<Set baud rate
  options.c_iflag = IGNPAR;
  options.c_oflag = 0;
  options.c_lflag = 0;
  tcflush(uart0_filestream, TCIFLUSH);
  tcsetattr(uart0_filestream, TCSANOW, &options);

  env->ReleaseByteArrayElements(device, buf, JNI_ABORT);
  
  return (jint) uart0_filestream;
  //return (jint) 3;	  
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialFlush
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialFlush
  (JNIEnv *, jobject, jint fd) {
    ;
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialClose
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialClose
  (JNIEnv *, jobject, jint fd) {
    close(fd);
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialPutchar
 * Signature: (IB)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialPutchar
  (JNIEnv *, jobject, jint fd, jbyte c) {
    //ftruncate(fd, 0);
    //lseek(fd, 0L, SEEK_SET);

    write(fd, &c, 1);
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialPuts
 * Signature: (I[B)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialPuts
  (JNIEnv *env, jobject, jint fd, jbyteArray s) {

  //ftruncate(fd, 0);
  //lseek(fd, 0L, SEEK_SET);

  jint len = env->GetArrayLength(s);
  jbyte* buf = env->GetByteArrayElements(s, 0);
  write(fd, buf, len);
  env->ReleaseByteArrayElements(s, buf, JNI_ABORT);
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialDataAvail
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialDataAvail
  (JNIEnv *, jobject, jint fd) {

    //lseek(fd, 0L, SEEK_END);
    //int size = lseek(fd, 0, SEEK_CUR);
    //lseek(fd, 0L, SEEK_SET);

    //if (size > 0)
    //    return (jint) size;
    //else
    //    return (jint) -1;

    return 0;
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialGetchar
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialGetchar
  (JNIEnv *, jobject, jint fd) {

  //lseek(fd, 0L, SEEK_END);
  //size_t fSize = (size_t) lseek(fd, 0, SEEK_CUR);
  //lseek(fd, 0L, SEEK_SET);

  //if (fSize <= 0)
  //    return (jint) -1;

  //char *buf = (char*) malloc(fSize);
  //char *buf2 = (char*) malloc(fSize - 1);
  //read(fd, buf, fSize);
  //ftruncate(fd, 0);
  //lseek(fd, 0L, SEEK_SET);

  //char value = buf[0];
  //memcpy(buf2, buf + 1, fSize - 1);
  //write(fd, buf2, fSize - 1);

  //free(buf);
  //free(buf2);

  //char rx = 0;
  //read(fd, &rx, 1);

  //return (jint) rx;
  //return (jint) -1;
  
    uint8_t x ;

    if (read (fd, &x, 1) != 1)
	    return (jint) -1 ;

    return (jint) (((int)x) & 0xFF) ;
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialReadExbPacket
 * Signature: (II)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialReadExbPacket
  (JNIEnv *env, jobject, jint fd, jint timeout) {
    RingBufferDef *inBuf  = RINGS_createRingBuffer(65000, RINGS_OVERFLOW_SHIFT, true);
    uint64_t prbits = EXB_PREAMBLE_R;
    uint8_t mode = MODE_PREAMBLE;
    ExbPacket *packet = NULL;
    uint16_t sbody = 0;

    while(1) {
        char ch;

        ssize_t r = read(fd, &ch, 1);
        if (r > 0) { // if some char was received
            printf("%02X", ch);
            fflush(stdout);
            RINGS_write((uint8_t) ch, inBuf);
            uint16_t dlen = RINGS_dataLenght(inBuf);
            if (mode == MODE_PREAMBLE) { // If expected preamble form stream
            	if (dlen >= EXB_PREAMBLE_SIZE) { //If the buffer contain data with size of preamble or more
            		int r = RINGS_cmpData(dlen - EXB_PREAMBLE_SIZE, (uint8_t*) &prbits, EXB_PREAMBLE_SIZE, inBuf);
            		if (r == 0) {
                		mode = MODE_HEADER;
                	}
                }
            } else if (mode == MODE_HEADER) {
                if (dlen >= EXB_HEADER_SIZE + EXB_PREAMBLE_SIZE) {
                    uint8_t *header = (uint8_t*) malloc(EXB_HEADER_SIZE);
                	packet = (ExbPacket*) malloc(sizeof(ExbPacket));
                	RINGS_extractData(inBuf->reader + EXB_PREAMBLE_SIZE, EXB_HEADER_SIZE, header, inBuf);
                	ExbPacket_parsePacketHeader(packet, header, 0);
                	sbody = packet->length;
                	if (sbody > 256 - (EXB_PREAMBLE_SIZE + EXB_HEADER_SIZE))
                        mode = MODE_PREAMBLE;
                    else
                        mode = MODE_BODY;
                	free(header);
                }
            } else {
                if (dlen >= sbody + EXB_HEADER_SIZE + EXB_PREAMBLE_SIZE) {
                    uint8_t *blob = (uint8_t*) malloc(sbody + EXB_HEADER_SIZE + EXB_PREAMBLE_SIZE);
                    RINGS_readAll(blob, inBuf);

                    int stotal = sbody + EXB_HEADER_SIZE + EXB_PREAMBLE_SIZE;
                    jbyteArray ret = env->NewByteArray(stotal);
                    env->SetByteArrayRegion(ret, 0, stotal, (jbyte*) blob);


                    free(blob);
                    free(packet);
                    RINGS_Free(inBuf);
                    free(inBuf);

                    return ret;
                    //mode = MODE_PREAMBLE;
                }
            }
        } else {
            jbyteArray ret = env->NewByteArray(0);
            return ret;
        }
    }
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainConnect
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainConnect
  (JNIEnv *env, jobject, jbyteArray path) {
    int sockfd = socket(AF_UNIX, SOCK_STREAM, 0);
    struct sockaddr_un address;
    address.sun_family = AF_UNIX;

    jsize arrs = env->GetArrayLength(path);
    jbyte* buf = env->GetByteArrayElements(path, 0);
    char *pathStr = new char[arrs + 1];
    memcpy(pathStr, buf, arrs);
    env->ReleaseByteArrayElements(path, buf, JNI_ABORT);
    pathStr[arrs] = 0;
    strcpy(address.sun_path, (const char*) pathStr);
    delete pathStr;

    size_t len = sizeof(address);

    int result = connect(sockfd, (struct sockaddr *)&address, len);
       if (result == -1) {
           return (jint) -1;
       } else {
           return (jint) sockfd;
       }

}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainReadChar
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainReadChar
  (JNIEnv *, jobject, jint sockfd) {
    char ch;
    if (read(sockfd, &ch, 1) > 0)
        return (jint) ch;
    else
        return (jint) -1;
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainWrite
 * Signature: (I[B)V
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainWrite
  (JNIEnv *env, jobject, jint sockfd, jbyteArray s) {
    jint len = env->GetArrayLength(s);
    jbyte* buf = env->GetByteArrayElements(s, 0);
    jint result = write(sockfd, buf, len);
    fsync(sockfd);

    env->ReleaseByteArrayElements(s, buf, JNI_ABORT);

    return result;
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainClose
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainClose
  (JNIEnv *, jobject, jint sockfd) {
    close(sockfd);
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    unixDomainReadWsdPacket
 * Signature: (II)[B
 */
JNIEXPORT jbyteArray JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_unixDomainReadWsdPacket
  (JNIEnv *env, jobject, jint socket, jint timeout) {
    RingBufferDef *inBuf  = RINGS_createRingBuffer(65535, RINGS_OVERFLOW_SHIFT, true);
    uint64_t prbits = WSD_PREAMBLE;
    uint8_t mode = MODE_PREAMBLE;
    WsdPacket *packet = NULL;
    uint16_t sbody = 0;

    while(1) {
        char ch;
        fflush(stdout);

        ssize_t r = read(socket, &ch, 1);
        if (r > 0) { // if some char was received
            RINGS_write((uint8_t) ch, inBuf);
            uint16_t dlen = RINGS_dataLenght(inBuf);
            if (mode == MODE_PREAMBLE) { // If expected preamble form stream
            	if (dlen >= WSD_PREAMBLE_SIZE) { //If the buffer contain data with size of preamble or more
            		int r = RINGS_cmpData(dlen - WSD_PREAMBLE_SIZE, (uint8_t*) &prbits, WSD_PREAMBLE_SIZE, inBuf);
            		if (r == 0) {
                		//RINGS_dataClear(inBuf);
                		mode = MODE_HEADER;
                	}
                }
            } else if (mode == MODE_HEADER) {
                if (dlen >= WSD_HEADER_SIZE + WSD_PREAMBLE_SIZE) {
                    uint8_t *header = (uint8_t*) malloc(WSD_HEADER_SIZE);
                	packet = (WsdPacket*) malloc(sizeof(WsdPacket));
                	RINGS_extractData(inBuf->reader + WSD_PREAMBLE_SIZE, WSD_HEADER_SIZE, header, inBuf);
                	WsdPacket_parsePacketHeader(packet, header, 0);
                	sbody = packet->length;
                	//if (sbody > 128)
                	//	mode = MODE_PREAMBLE;
                	mode = MODE_BODY;
                	free(header);
                }
            } else {
                if (dlen >= sbody + WSD_HEADER_SIZE + WSD_PREAMBLE_SIZE) {
                    uint8_t *blob = (uint8_t*) malloc(sbody + WSD_HEADER_SIZE + WSD_PREAMBLE_SIZE);
                    RINGS_readAll(blob, inBuf);

                    int stotal = sbody + WSD_HEADER_SIZE + WSD_PREAMBLE_SIZE;
                    jbyteArray ret = env->NewByteArray(stotal);
                    env->SetByteArrayRegion(ret, 0, stotal, (jbyte*) blob);


                    free(blob);
                    free(packet);
                    RINGS_Free(inBuf);
                    free(inBuf);

                    return ret;
                    //mode = MODE_PREAMBLE;
                }
            }
        } else {
            jbyteArray ret = env->NewByteArray(0);
            return ret;
        }
    }
}
