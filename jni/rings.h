/*
 * rings.h
 *
 *  Created on: 29 окт. 2017 г.
 *      Author: serbis
 */

#ifndef LIBS_RINGS_H_
#define LIBS_RINGS_H_ 1

#include <stdint.h>

/** Политика переполнения, при которой ридер будет сдвигатья вправо затирая
 * не считанные данные */
#define RINGS_OVERFLOW_SHIFT 0

/** Use pthread mutex */
//#define MUTEX_PTHREAD

/** Определение кольцевого буфера */
typedef struct {
	uint8_t *buffer;
	uint16_t reader;
    uint16_t writer;
    uint16_t size;
	#ifdef MUTEX_PTHREAD
    pthread_mutex_t *mutex;
	#endif
    uint8_t overflowPolitics;
} RingBufferDef;


RingBufferDef* RINGS_createRingBuffer(uint16_t size, uint8_t overflowPolitics, uint8_t portMalloc);
void RINGS_Free(RingBufferDef* rbd);
uint8_t RINGS_read(RingBufferDef* rbd);
uint8_t RINGS_write(uint8_t byte, RingBufferDef* rbd);
uint8_t RINGS_writeArray(uint8_t *array, uint16_t size, RingBufferDef* rbd);
uint16_t RINGS_readAll(uint8_t *buff, RingBufferDef* rbd);
char* RINGS_readString(uint16_t lenght, RingBufferDef* rbd);
char* RINGS_readStringInRange(uint16_t start, uint16_t lenght, RingBufferDef* rbd);
uint16_t RINGS_dataLenght(RingBufferDef* rbd);
uint8_t* RINGS_dataStart(RingBufferDef* rbd);
void RINGS_dataClear(RingBufferDef* rbd);
void RINGS_dataClearFull(RingBufferDef* rbd);
void RINGS_dataClearBySize(uint16_t size, RingBufferDef* rbd);
uint8_t RINGS_getByShiftFromWriter(int32_t shift, RingBufferDef* rbd);
uint8_t RINGS_getByShiftFromReader(int32_t shift, RingBufferDef* rbd);
void RINGS_shiftReader(int32_t shift, RingBufferDef* rbd);
void RINGS_shiftWriter(int32_t shift, RingBufferDef* rbd);
uint16_t RINGS_extractData(int32_t position, uint16_t size, uint8_t *buffer, RingBufferDef* rbd);
uint8_t RINGS_cmpData(uint16_t readerOffset, uint8_t *cmpData, uint16_t dataSize, RingBufferDef* rbd);
uint8_t RINGS_cmpDataReverse(uint16_t writerOffset, uint8_t *cmpData, uint16_t dataSize, RingBufferDef* rbd);

#endif /* LIBS_RINGS_H_ */
