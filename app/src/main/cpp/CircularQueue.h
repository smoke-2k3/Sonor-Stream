#ifndef SONOR_STREAM_CIRCULARQUEUE_CPP
#define SONOR_STREAM_CIRCULARQUEUE_CPP

#include <iostream>
#include <cstdint>
#define SIZE 5

using namespace std;

template <typename T>
class CircularQueue {
private:
    T* arr;
    int fIndex, rIndex;
    int capacity;  // The capacity of the queue
    int elementSize;  // The size of each element in bytes

public:
    explicit CircularQueue(int n) : capacity(SIZE), elementSize(n) {
        arr = new T[capacity];
        fIndex = 0;
        rIndex = 0;
    }

    bool isFull() {
        return (rIndex + 1) % capacity == fIndex;
    }

    bool isEmpty() {
        return rIndex == fIndex;
    }

    bool enqueue(T item) {
        if (isFull()) {
            return false;
        }
        arr[rIndex] = item;
        rIndex = (rIndex + 1) % capacity;
        return true;
    }

    void dequeue() {
        if (isEmpty()) {
            return;
        }
        fIndex = (fIndex + 1) % capacity;
    }

    T front() {
        return isEmpty() ? nullptr : arr[fIndex];
    }
};

#endif //SONOR_STREAM_CIRCULARQUEUE_CPP
