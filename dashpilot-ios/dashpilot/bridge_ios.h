#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Context
void* bridge_create_context(void);
void  bridge_delete_context(void* ctx);

// Subscribe socket group
void* bridge_create_sub_sockets(void* ctx, const char** endpoints, int count, const char* address);
void  bridge_delete_sub_sockets(void* group);

// Receive loop
typedef void (*bridge_callback_t)(const uint8_t* data, int size);
void bridge_start_receive_loop(void* group, bridge_callback_t callback);
void bridge_stop_receive_loop(void);

#ifdef __cplusplus
}
#endif
