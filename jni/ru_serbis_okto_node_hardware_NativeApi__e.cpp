#include "ru_serbis_okto_node_hardware_NativeApi__.h"
#include <iostream>
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

extern "C" {
#include <time.h>
}

JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_wiringPiSetupSys
  (JNIEnv *, jobject) {
    return (jint) 0;
}
/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialOpen
 * Signature: ([BI)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialOpen
  (JNIEnv* env, jobject, jbyteArray device, jint baud) {

  jbyte* buf = env->GetByteArrayElements(device, 0);
  int fd = open((const char*) buf, O_RDWR | O_CREAT, 0644);
  env->ReleaseByteArrayElements(device, buf, JNI_ABORT);

  return (jint) fd;
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialFlush
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialFlush
  (JNIEnv *, jobject, jint) {

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
    ftruncate(fd, 0);
    lseek(fd, 0L, SEEK_SET);

    write(fd, &c, 1);
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialPuts
 * Signature: (I[B)V
 */
JNIEXPORT void JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialPuts
  (JNIEnv *env, jobject, jint fd, jbyteArray s) {

  ftruncate(fd, 0);
  lseek(fd, 0L, SEEK_SET);

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

    lseek(fd, 0L, SEEK_END);
    int size = lseek(fd, 0, SEEK_CUR);
    lseek(fd, 0L, SEEK_SET);

    if (size > 0)
        return (jint) size;
    else
        return (jint) -1;
}

/*
 * Class:     ru_serbis_okto_node_hardware_NativeApi__
 * Method:    serialGetchar
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_ru_serbis_okto_node_hardware_NativeApi_00024_serialGetchar
  (JNIEnv *, jobject, jint fd) {

  lseek(fd, 0L, SEEK_END);
  size_t fSize = (size_t) lseek(fd, 0, SEEK_CUR);
  lseek(fd, 0L, SEEK_SET);

  if (fSize <= 0)
      return (jint) -1;

  char *buf = (char*) malloc(fSize);
  char *buf2 = (char*) malloc(fSize - 1);
  read(fd, buf, fSize);
  ftruncate(fd, 0);
  lseek(fd, 0L, SEEK_SET);

  char value = buf[0];
  memcpy(buf2, buf + 1, fSize - 1);
  write(fd, buf2, fSize - 1);

  free(buf);
  free(buf2);

  return (jint) value;
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