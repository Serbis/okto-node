/*
 * rings.c
 *
 *  Created on: 29 окт. 2017 г.
 *      Author: serbis
 */

/**
 * Реализация кольцевого буфера
 *
 * Версия: 8
 *
 */

#include <stdlib.h>
#include <string.h>
#include "rings.h"

/**
 * Создает новый кольцевой буфер в динамической памяти
 *
 * @param size размер буфера в байтах
 * @param portMalloc 1 для использования менеджера памяти уровня ос
 * @return указатель на определение кольцевого буфера
 */
RingBufferDef* RINGS_createRingBuffer(uint16_t size, uint8_t overflowPolitics, uint8_t portMalloc) {
	RingBufferDef *rbd;
	uint8_t *buffer;


	//if (portMalloc == 0) {
		rbd = (RingBufferDef*) malloc(sizeof(RingBufferDef));
		buffer = (uint8_t*) malloc(size);
	//} else {
	//	rbd = (RingBufferDef*) pmalloc(sizeof(RingBufferDef));
	//	buffer = (uint8_t*) pmalloc(size);
	//}

	rbd->buffer = buffer;
	rbd->reader = 0;
	rbd->writer = 0;
    rbd->size = size;
	#ifdef MUTEX_PTHREAD
    rbd->mutex = NewMutex();
	#endif
    rbd->overflowPolitics = overflowPolitics;

	return rbd;
}

/**
 * Деструктор кольцевого буфера
 *
 * @param rbd определение кольца
 */
void RINGS_Free(RingBufferDef* rbd) {
	free(rbd->buffer);
}

/**
 * Считывает один байт из буфера
 *
 * @param rbd определение кольца
 * @return считанный байт
 */
uint8_t RINGS_read(RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif

	uint8_t b = *(rbd->buffer + rbd->reader);
    if (rbd->reader == rbd->size -1) {
        rbd->reader = 0;
    } else {
        if (rbd->reader != rbd->writer)
            rbd->reader++;
    }

    #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif

	return b;
}

/**
 * Записывает дин байт в указанный буфер
 *
 * @param byte байт для записи
 * @param rbd определение кольца
 * @return количество записанных байт (в рамках функции всегда равно 1)
 */
uint8_t RINGS_write(uint8_t byte, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	*(rbd->buffer + rbd->writer) = byte;
    if (rbd->writer == rbd->size -1) {
        if (rbd->reader == 0) {
            switch (rbd->overflowPolitics) {
                case RINGS_OVERFLOW_SHIFT:
                    if (rbd->reader == rbd->size -1)
                        rbd->reader = 0;
                    else
                        rbd->reader++;
                    break;

                default:break;
            }
        }
        rbd->writer = 0;
    } else {
        if (rbd->writer == rbd->reader - 1) {
            switch (rbd->overflowPolitics) {
                case RINGS_OVERFLOW_SHIFT:
                    if (rbd->reader == rbd->size -1)
                        rbd->reader = 0;
                    else
                        rbd->reader++;
                    break;

                default:break;
            }
        }
        rbd->writer++;
    }

    #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return 1;
}

/**
 * Записывает в буфер массив данных
 *
 * @param array указатель на массив данных
 * @param size размер массива данных
 * @param rbd представление кольца
 * @return количество записанных в буфер байт
 */
uint8_t RINGS_writeArray(uint8_t *array, uint16_t size, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	for (int i = 0; i < size; i++) {
		RINGS_write(*(array + i), rbd);
	}

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return size;
}

/**
 * Считывает все имеющиеся в кольце в указанный буфер
 *
 * @param buff буфер в который происходит перенос данных
 * @param rbd определение кольца
 * @return количество перенесенных байт
 */
uint16_t RINGS_readAll(uint8_t *buff, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	uint16_t p = 0;

	while (rbd->reader != rbd->writer) {
		*(buff + p) = *(rbd->buffer + rbd->reader);
		p++;
		if (rbd->reader > rbd->size - 2) {
			rbd->reader = 0;
		} else {
			rbd->reader++;
		}
	}

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return p;
}

/**
 * Возвращает данные из буфера как строку (с добавлением терминатора к конец
 * блока данных)
 *
 * @param lenght размер выборки данных
 * @param rbd определение кольца
 * @return указатель на созданную строку
 */
char* RINGS_readString(uint16_t lenght, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	uint16_t strSize = rbd->writer - rbd->reader + 1;
	char* str = (char*) malloc(strSize);
	memcpy(str, (uint8_t*) (rbd->buffer + rbd->reader), strSize - 1);
	*(str + strSize - 1) = 0;

	rbd->reader = rbd->writer;

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return str;
}


/**
 * Возвращает данные из буфера как строку из заданного диапазона (с добавлением
 * терминатора к конец блока данных)
 *
 * @param start позиция начала чтения
 * @param lenght размер выборки данных
 * @param rbd определение кольца
 * @return указатель на созданную строку
 */
char* RINGS_readStringInRange(uint16_t start, uint16_t lenght, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	char* str = (char*) malloc(lenght + 1);
	uint16_t count = 0;
	uint16_t ptr = start;

	while(count < lenght) {
		*(str + count) = *(rbd->buffer + ptr);

		if (ptr == rbd->size -1) {
			ptr = 0;
		} else {
		    ptr++;
		}

		count++;
	}

	*(str + lenght) = 0;

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return str;

}

/**
 * Возвращает количество данных в кольце
 *
 * @param rbd определение кольца
 * @return количество байт
 */
uint16_t RINGS_dataLenght(RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	if (rbd->writer < rbd->reader) {
		#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


		return rbd->size - rbd->reader + rbd->writer;
	}

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return rbd->writer - rbd->reader;
}

/**
 * Возвращает указатель на начало блока данных в кольце
 *
 * @param rbd определение кольца
 * @return указатель на начало блока данных
 */
uint8_t* RINGS_dataStart(RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	return rbd->buffer + rbd->reader;
	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


}



/**
 * Очищает блок данных в кольце (читай устанавливает редер на позицию райтера)
 *
 * @param rbd определение кольца
 */
void RINGS_dataClear(RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	rbd->reader = rbd->writer;
	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


}

/**
 * Очищает блок данных в кольце, заполняет весь буфер нулями, устанавливает
 * ридет и райтер в нулевую позицию
 *
 * @param rbd определение кольца
 */
void RINGS_dataClearFull(RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	rbd->reader = 0;
	rbd->writer = 0;

    for (int i = 0; i < rbd->size - 1; i++) {
    	*(rbd->buffer + i) = 0;
    }
    #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


}

/**
 * Очищает блок данных заданного размера в кольце
 *
 * @param size размер очищаемого блока от позиции ридера
 * @param rbd определение кольца
 */
void RINGS_dataClearBySize(uint16_t size, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	rbd->reader = rbd->reader + size;
	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


}

/**
 * Возвращает байт по смещения от райтера. Не изменяет значение
 * райтера.
 *
 * Примечание: при использовании функции, следует помнить то, что
 * райтер всегда находится на один байт впереди последнего реально
 * записанного байта. Поэтому фактически первый байт по райтеру
 * находится на позиции по смещению -1.
 *
 * @param shift положительное или отрицательное смещение
 * @param rbd определение кольца
 */
uint8_t RINGS_getByShiftFromWriter(int32_t shift, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


    if (shift > 0) { //Если искомы байт находится впереди от райтера
        uint16_t ns = shift;
        uint16_t ptr = rbd->writer;
        while (ns > 0) {
            if (ptr == rbd->size -1) {
                ptr = 0;
            } else {
                ptr++;
            }
            ns--;
        }

        #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


        return *(rbd->buffer + ptr);

    } else if (shift < 0) { //Если искомый байт находится сзади от райтера
        int32_t ns = shift;
        uint16_t ptr = rbd->writer;
        while (ns < 0) {
            if (ptr == 0) {
                ptr = rbd->size - 1;
            } else {
                ptr--;
            }
            ns++;
        }

        #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


        return *(rbd->buffer + ptr);

    } else { //Если смещение равно нулю
    	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


        return *(rbd->buffer + rbd->writer);
    }
}

/**
 * Возвращает байт по смещения от ридера. Не изменяет значение
 * ридера.
 *
 *
 * @param shift положительное или отрицательное смещение
 * @param rbd определение кольца
 */
uint8_t RINGS_getByShiftFromReader(int32_t shift, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


    if (shift > 0) { //Если искомы байт находится впереди от ридера
        uint16_t ns = shift;
        uint16_t ptr = rbd->reader;
        while (ns > 0) {
            if (ptr == rbd->size -1) {
                ptr = 0;
            } else {
                ptr++;
            }
            ns--;
        }

        #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


        return *(rbd->buffer + ptr);

    } else if (shift < 0) { //Если искомый байт находится сзади от райтера
        int32_t ns = shift;
        uint16_t ptr = rbd->reader;
        while (ns < 0) {
            if (ptr == 0) {
                ptr = rbd->size - 1;
            } else {
                ptr--;
            }
            ns++;
        }

        #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


        return *(rbd->buffer + ptr);

    } else { //Если смещение равно нулю
    	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


        return *(rbd->buffer + rbd->reader);
    }
}

/**
 * Сдвигает ридер кольца на заданное количество байт
 *
 * @param shift положительное или отрицательное число сдвига
 * @param rbd определение кольца
 */
void RINGS_shiftReader(int32_t shift, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


    if (shift > 0) { //Если искомы байт находится впереди от райтера
        uint16_t ns = shift;
        uint16_t ptr = rbd->reader;
        while (ns > 0) {
            if (ptr == rbd->size -1) {
                ptr = 0;
            } else {
                ptr++;
            }
            ns--;
        }
        rbd->reader = ptr;
    } else if (shift < 0) { //Если искомый байт находится сзади от райтера
        int32_t ns = shift;
        uint16_t ptr = rbd->reader;
        while (ns < 0) {
            if (ptr == 0) {
                ptr = rbd->size - 1;
            } else {
                ptr--;
            }
            ns++;
        }
        rbd->reader = ptr;
    }
    #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


}

/**
 * Сдвигает райтер кольца на заданное количество байт
 *
 * @param shift положительное или отрицательное число сдвига
 * @param rbd определение кольца
 */
void RINGS_shiftWriter(int32_t shift, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


    if (shift > 0) { //Если искомы байт находится впереди от райтера
        uint16_t ns = shift;
        uint16_t ptr = rbd->writer;
        while (ns > 0) {
            if (ptr == rbd->size -1) {
                ptr = 0;
            } else {
                ptr++;
            }
            ns--;
        }
        rbd->writer = ptr;
    } else if (shift < 0) { //Если искомый байт находится сзади от райтера
        int32_t ns = shift;
        uint16_t ptr = rbd->writer;
        while (ns < 0) {
            if (ptr == 0) {
                ptr = rbd->size - 1;
            } else {
                ptr--;
            }
            ns++;
        }
        rbd->writer = ptr;
    }
    #ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


}

/**
 * Извлекает из буфера некоторый объекм данных в байтовый массив
 *
 * @param position позиция начала выборки
 * @param size количество байт для выпборки
 * @prarm buffer указатель на буфер в который будет размещены данные
 * @param rbd определение кольца
 *
 * @return кол-во фактически записнных в буфер байт
 */
uint16_t RINGS_extractData(int32_t position, uint16_t size, uint8_t *buffer, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	uint16_t wptr = 0;
	uint16_t writed = 0;
	uint16_t realPosition = 0;
	if (position >= 0)
		realPosition = position;
	else
		realPosition = rbd->size - (0 - position);

	while (writed < size) {
		buffer[writed] = rbd->buffer[realPosition + wptr];
		writed++;
		if (realPosition + wptr > rbd->size - 2) {
			realPosition = 0;
			wptr = 0;
		} else {
			wptr++;
		}
	}

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return wptr;
}

uint8_t RINGS_cmpData(uint16_t readerOffset, uint8_t *cmpData, uint16_t dataSize, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	uint16_t count = 0;
	uint16_t ptr = rbd->reader + readerOffset;
	if (ptr > rbd->size)
		ptr = ptr - rbd->size;

	while(count < dataSize) {
		volatile char ch = *(rbd->buffer + ptr);
		volatile char cmpch = *(cmpData + count);
		if (ch != cmpch) {
			ptr = ch;
			#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


			return -1;
		}

		if (ptr == rbd->size -1) {
			ptr = 0;
		} else {
		    ptr++;
		}

		count++;
	}

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return 0;
}

uint8_t RINGS_cmpDataReverse(uint16_t writerOffset, uint8_t *cmpData, uint16_t dataSize, RingBufferDef* rbd) {
	#ifdef MUTEX_PTHREAD
	MutexLock(rbd->mutex);
	#endif


	uint16_t count = dataSize;
	uint16_t ptr = rbd->writer + writerOffset - 1;

	while(count > 0) {
		char ch = *(rbd->buffer + ptr);
		if (*(rbd->buffer + ptr) != *(cmpData + count - 1)) {
			ptr = ch;
			#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


			return -1;
		}

		if (ptr == 0) {
		    ptr = rbd->size - 1;
		} else {
		    ptr--;
		}

		count--;
	}

	#ifdef MUTEX_PTHREAD
    MutexUnlock(rbd->mutex);
	#endif


	return 0;
}
