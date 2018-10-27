#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

// The Gimple Garbage Collector.


//===============================================================//
// The Java Heap data structure.

/*   
      ----------------------------------------------------
      |                        |                         |
      ----------------------------------------------------
      ^\                      /^
      | \<~~~~~~~ size ~~~~~>/ |
    from                       to
 */
struct JavaHeap
{
  int size;         // in bytes, note that this if for semi-heap size
  char *from;       // the "from" space pointer
  char *fromFree;   // the next "free" space in the from space
  char *to;         // the "to" space pointer
  char *toStart;    // "start" address in the "to" space
  char *toNext;     // "next" free space pointer in the to space
};

// The Java heap, which is initialized by the following
// "heap_init" function.
struct JavaHeap heap;

// Lab 4, exercise 10:
// Given the heap size (in bytes), allocate a Java heap
// in the C heap, initialize the relevant fields.
void Tiger_heap_init (int heapSize)
{
  // You should write 8 statement here:
  // #1: Allocate a chunk of memory of size "heapSize" using "malloc"
  char* heap_start = (char*) malloc(heapSize);
  // #2: Clear the aloocated heap
  memset(heap_start, 0, heapSize);
  // #3: Initialize the "size" field, note that "size" field
  // is for semi-heap, but "heapSize" is for the whole heap.
  heap.size = heapSize / 2;
  // #4: Initialize the "from" field (with what value?)
  heap.from = heap_start;
  // #5: Initialize the "fromFree" field (with what value?)
  heap.fromFree = heap_start;
  // #6: Initialize the "to" field (with what value?)
  heap.to = heap_start + heap.size;
  // #7: Initizlize the "toStart" field with heap.to;
  heap.toStart = heap.to;
  // #8: Initialize the "toNext" field with heap.to;
  heap.toNext = heap.to;

  // Print out the information of the initialized heap
  printf("The Java Heap init info :\n"
      "----the heapSize : %d\n"
      "----the 'from' pointer : 0x%p\n"
      "----the 'fromFree' pointer : 0x%p\n"
      "----the 'to' pointer : 0x%p\n\n", heapSize, heap.from,
      heap.fromFree, heap.to);
  return;
}

// The "prev" pointer, pointing to the top frame on the GC stack. 
// (see part A of Lab 4)
extern void *prev;
// The "forwarded_number" stores the forwarded objects in a round of GC.
int forwarded_number = 0;


//===============================================================//
// Object Model And allocation


// Lab 4: exercise 11:
// "new" a new object, do necessary initializations, and
// return the pointer (reference).
/*    ----------------
      | vptr      ---|----> (points to the virtual method table)
      |--------------|
      | isObjOrArray | (0: for normal objects)
      |--------------|
      | length       | (this field should be empty for normal objects)
      |--------------|
      | forwarding   | 
      |--------------|\
p---->| v_0          | \      
      |--------------|  s
      | ...          |  i
      |--------------|  z
      | v_{size-1}   | /e
      ----------------/
*/
// Try to allocate an object in the "from" space of the Java
// heap. Read Tiger book chapter 13.3 for details on the
// allocation.
// There are two cases to consider:
//   1. If the "from" space has enough space to hold this object, then
//      allocation succeeds, return the apropriate address (look at
//      the above figure, be careful);
//   2. If there is no enough space left in the "from" space, then
//      you should call the function "Tiger_gc()" to collect garbages.
//      and after the collection, there are still two sub-cases:
//        a: if there is enough space, you can do allocations just as case 1; 
//        b: if there is still no enough space, you can just issue
//           an error message ("OutOfMemory") and exit.
//           (However, a production compiler will try to expand
//           the Java heap.)
void *Tiger_new (void *vtable, int size)
{
  int used_size = heap.fromFree - heap.from;
  int remain_size = heap.size - used_size;
  if (remain_size < size) {
    printf("----------------------------------------------------------\n");
    printf("There are no enough space! Garbage collection begins.\n");
    Tiger_gc();
    printf("End of garbage collection.\n");
    printf("----------------------------------------------------------\n");
  }

  // See if there are enough space after gc
  used_size = heap.fromFree - heap.from;
  remain_size = heap.size - used_size; 
  if (remain_size < size) {
    printf("There are no enough space after gc!\n");
    exit(1);
  }

  // You should write 4 statements for this function
  // #1: Get the start position of the new object
  void* object = heap.fromFree; 
  // #2: Clear this chunk of memory (zero off it)
  memset(object, 0, size);
  // #3: Initialize the parameters of this object:vptr, isObjOrArray and length 
  memcpy(object, &vtable, sizeof(void*));
  int *isObjOrArrayPtr = (int*) (object + sizeof(void*));
  *isObjOrArrayPtr = 0;
  int *lengthPtr = (int*) (object + sizeof(void*) + sizeof(int));
  *lengthPtr = 0;
  // #4: Update the heap.fromFree
  heap.fromFree = heap.fromFree + size;
  // #5: Return the pointer of the object
  printf("Allocated the object at 0x%p\n", object);
  return object;
}

// "new" an array of size "length", do necessary
// initializations. And each array comes with an
// extra "header" storing the array length and other information.
/*    ----------------
      | vptr         | (this field should be empty for an array)
      |--------------|
      | isObjOrArray | (1: for array)
      |--------------|
      | length       |
      |--------------|
      | forwarding   | 
      |--------------|\
p---->| e_0          | \      
      |--------------|  s
      | ...          |  i
      |--------------|  z
      | e_{length-1} | /e
      ----------------/
*/
// Try to allocate an array object in the "from" space of the Java
// heap. Read Tiger book chapter 13.3 for details on the
// allocation.
// There are two cases to consider:
//   1. If the "from" space has enough space to hold this array object, then
//      allocation succeeds, return the apropriate address (look at
//      the above figure, be careful);
//   2. if there is no enough space left in the "from" space, then
//      you should call the function "Tiger_gc()" to collect garbages.
//      and after the collection, there are still two sub-cases:
//        a: if there is enough space, you can do allocations just as case 1; 
//        b: if there is still no enough space, you can just issue
//           an error message ("OutOfMemory") and exit.
//           (However, a production compiler will try to expand
//           the Java heap.)
void *Tiger_new_array (int length)
{
  // Calculate the size of the IntArray and see if there is enough space.
  int array_size = sizeof(void*) + 2 * sizeof(int) + sizeof(void*) + length * sizeof(int);
  int used_size = heap.fromFree - heap.from;
  int remain_size = heap.size - used_size;
  if (remain_size < array_size) {
    printf("----------------------------------------------------------\n");
    printf("There are no enough space! Garbage collection begins.\n");
    Tiger_gc();
    printf("End of garbage collection.\n");
    printf("----------------------------------------------------------\n");
  }

  // See if there are enough space after gc
  used_size = heap.fromFree - heap.from;
  remain_size = heap.size - used_size; 
  if (remain_size < array_size) {
    printf("There are no enough space after gc\n");
    exit(1);
  }

  // #2: Get the start position of the new array
  void *array = heap.fromFree; 
  // #3: Clear this chunk of memory (zero off it)
  memset(array, 0, array_size);
  // #4 set the isObjectOrArray and the length
  int *isObjectOrArrayPtr = array + sizeof(void*);
  *isObjectOrArrayPtr = 1;
  int *lengthPtr = array + sizeof(void*) + sizeof(int);
  *lengthPtr = length;
  // #5: Update the heap.fromFree
  heap.fromFree = heap.fromFree + array_size;
  // #6: Return the pointer of the array
  printf("Allocated the array at 0x%p\n", array);
  return array;
}

//===============================================================//
// The Gimple Garbage Collector

int forward(void* objectOrArray) {
  if (((char*) objectOrArray) < heap.from || ((char*) objectOrArray) > (heap.from + heap.size)) {
    // The address of the forwarded object is not in the "from" space.
    return -1;
  }
  void* forwarding = *(void**)(objectOrArray + sizeof(void*) + 2 * sizeof(int));
  int isObjOrArray = *(int*)(objectOrArray + sizeof(void*));
  if (forwarding == 0) {
    // This object or array has not been forwarded.
    // The "forwarded_number" accumulates the forwarded objects in ths round of GC.
    forwarded_number++;
    if (isObjOrArray == 0) {
      // It is an object
      void* object_vtable = *(void**) objectOrArray;
      char* class_gc_map = *(char**) object_vtable;
      // Calculate the size of this object from the gc_map
      int size = sizeof(void*) + 2 * sizeof(int) + sizeof(void*);
      for (int i = 0; i < strlen(class_gc_map); i++) {
        if (class_gc_map[i] == '1') {
          // object or array
          size += sizeof(void*);
        } else {
          // int
          size += sizeof(int);
        }
      }
      // DFS the accessed objects in fields
      void** fields_address = (void**)(objectOrArray + 2 * sizeof(void*) + 2 * sizeof(int));
      for (int i = 0; i < strlen(class_gc_map); i++) {
        if (class_gc_map[i] == '1') {
          // object or array
          void* field = *(fields_address + i);
          forward(field);
        }
      }
      // Copy the object to the "to" space.
      memcpy(heap.toNext, objectOrArray, size);
      // Set the forwarding using the address of the object in the "to" space.
      *(void**)(objectOrArray + sizeof(void*) + 2 * sizeof(int)) = heap.toNext;
      // Update the heap.toNext
      heap.toNext = heap.toNext + size;
      return 0;
    } else if (isObjOrArray == 1) {
      // It is an int array
      // Calculate the size of the array.
      int length = *(int*)(objectOrArray + sizeof(void*) + sizeof(int));
      int size = sizeof(void*) + 2 * sizeof(int) + sizeof(void*) + length;
      // Copy the array to the "to" space.
      memcpy(heap.toNext, objectOrArray, size);
      // Set the forwarding using the address of the object in the "to" space.
      *(void**)(objectOrArray + sizeof(void*) + 2 * sizeof(int)) = heap.toNext;
      // Update the heap.toNext
      heap.toNext = heap.toNext + size;
      return 0;
    }
  } else {
    // This object or array has already been forwarded.
    return 0;
  }
}

// Lab 4, exercise 12:
// A copying collector based-on Cheney's algorithm.
void Tiger_gc ()
{
  // #1. Go through the prev list to visit every root.
  void* scan = prev;
  while (scan != 0) {
    char* arguments_gc_map = *(char**)(scan + sizeof(void *));
    int* arguments_base_address = *(int**)(scan + sizeof(void *) + sizeof(char*));
    char* locals_gc_map = *(char**)(scan + sizeof(void *) + sizeof(char*) + sizeof(int*));
    
    // Forward the formals
    for (int i = 0; i < strlen(arguments_gc_map); i++) {
      if (arguments_gc_map[i] == '1') {
        // The "current_formal" is the address in the from space.
        void* current_formal = *(void**)(arguments_base_address + i);
        forward(current_formal);
      }
    }

    // Forward the locals
    void** locals_base_address = (void**)(scan + sizeof(void*) + 2 * sizeof(char*) + sizeof(int*));
    for (int i = 0; i < strlen(locals_gc_map); i++) {
      if (locals_gc_map[i] == '1') {
        // current_local is the address in the from space.
        void* current_local = *(locals_base_address + i);
        forward(current_local);
      }
    }
    scan = *(void**)scan;
  }

  // Change the address of formals and locals on prev from 
  // "from" space to "to" space using "forwarding".
  scan = prev;
  while (scan != 0) {
    char* arguments_gc_map = *(char**)(scan + sizeof(void *));
    int* arguments_base_address = *(int**)(scan + sizeof(void *) + sizeof(char*));
    char* locals_gc_map = *(char**)(scan + sizeof(void *) + sizeof(char*) + sizeof(int*));

    // formals
    for (int i = 0; i < strlen(arguments_gc_map); i++) {
      if (arguments_gc_map[i] == '1') {
        void* current_formal = *(void**)(arguments_base_address + i);
        // Get the forawrding of this object
        void* current_forwarding = *(void**)(current_formal + sizeof(void*) + 2 * sizeof(int));
        // Change the address of formals and locals on prev using forwarding.
        *(void**)(arguments_base_address + i) = current_forwarding;
      }
    }

    // locals
    void** locals_base_address = (void**)(scan + sizeof(void*) + 2 * sizeof(char*) + sizeof(int*));
    for (int i = 0; i < strlen(locals_gc_map); i++) {
      if (locals_gc_map[i] == '1') {
        void* current_local = *(locals_base_address + i);
        if (((char*) current_local) < heap.from || ((char*) current_local) > (heap.from + heap.size)) {
          continue;
        }
        void* current_forwarding = *(void**)(current_local + sizeof(void*) + 2 * sizeof(int));
        *(locals_base_address + i) = current_forwarding;
      }
    }
    scan = *(void**)scan;
  }

  // Change the address of the fields in objects in the "to" space
  void* object_base_address = heap.toStart;
  while ((*(void**)object_base_address) != 0) {
    int isObjectOrArray = *(void**) (object_base_address + sizeof(void*));
    if (isObjectOrArray == 0) {
      // object
      int size = 2 * sizeof(void*) + 2 * sizeof(int);
      void* object_vtable = *(void**) object_base_address;
      char* object_gc_map = *(char**) object_vtable;
      void** object_fields = (void**)(object_base_address + 2 * sizeof(void*) + 2 * sizeof(int));
      for (int i = 0; i < strlen(object_gc_map); i++) {
        if (object_gc_map[i] == '1') {
          size += sizeof(void*);
        } else {
          size += sizeof(int);
        }
        // Originally, field stores the address in the from space.
        void* field = *(object_fields + i);
        // Skip the field that has not been initiated.
        if (((char*) field) < heap.from || ((char*) field) > (heap.from + heap.size)) {
          continue;
        }
        void* field_forwarding = *(void**) (field + sizeof(void*) + 2 * sizeof(int));
        *(object_fields + i) = field_forwarding;
      }
      object_base_address += size;
    } else {
      // IntArray does not have the fields, but we still have to calculate the size
      // of the IntArray
      int length = *(int*)(object_base_address + sizeof(void*) + sizeof(int));
      int size = 2 * sizeof(void*) + 2 * sizeof(int) + length * sizeof(int);
      object_base_address += size;
    }
  }
  // swap
  char* tmp_toStart = heap.toStart;
  char* tmp_to = heap.to;
  char* tmp_toNext = heap.toNext;
  heap.to = heap.from;
  heap.toStart = heap.from;
  heap.toNext = heap.from;
  // Clear the new to space.
  memset(heap.to, 0, heap.size);
  heap.from = tmp_to;
  heap.fromFree = tmp_toNext;

  printf("The Java Heap init info after garbage collection:\n"
      "----the heapSize : %d\n"
      "----the 'from' pointer : 0x%p\n"
      "----the 'fromFree' pointer : 0x%p\n"
      "----the 'to' pointer : 0x%p\n", 2 * heap.size, heap.from,
      heap.fromFree, heap.to);

  // Zero off the forwarded_number.
  forwarded_number = 0;
}

