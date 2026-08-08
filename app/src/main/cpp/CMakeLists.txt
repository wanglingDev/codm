cmake_minimum_required(VERSION 3.10.2)
project(GaerisPatcher)

# Build a standalone executable, not a shared library
add_executable(patcher patcher_main.c)
target_link_libraries(patcher log)

# Strip the binary to keep it tiny
set_target_properties(patcher PROPERTIES
    LINK_FLAGS "-s -static-libgcc"
)

# Copy the built binary into assets/
add_custom_command(TARGET patcher POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
        $<TARGET_FILE:patcher>
        ${CMAKE_SOURCE_DIR}/../assets/patcher_arm64
    COMMENT "Copying patcher binary to assets"
)
