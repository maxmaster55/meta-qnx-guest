SUMMARY = "Motor data producer: reads the STM32 over SPI and publishes to shared memory"
DESCRIPTION = "The QNX-SPI side of the STM32 motor data producer. Reads the \
sensor stream over SPI and writes it into the shared-memory region motor_ai_client \
reads from, so it is the first thing /scripts/start-guest1.sh launches. Its two \
headers are staged as well: they define the wire format and the shared-memory \
layout, and are the contract between the two programs."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-src

QNX_SRC_REPO = "git://github.com/Mintharah/SPI-Stm32-QNX.git;protocol=https;branch=spi_qnx_build"

# The repository holds both sides. QNX-SPI is the QNX one; Stm32-SPI is firmware
# for the microcontroller at the other end of the wire and is not built here.
QNX_SRC_SUBDIR = "QNX-SPI"

# sys/rpi_gpio.h, which arrives in the sysroot.
#
# The project's CMakeLists reaches for it with
#   include_directories(../../rpi-gpio/resmgr/public)
# -- a sibling directory that only exists inside the hypervisor monorepo. That
# path is left alone (a non-existent include directory is not an error to cmake,
# just a -I nothing matches) and the real one is added through CFLAGS below, the
# same way motor-controller solves the same problem.
DEPENDS = "rpi-gpio"

# Folded into CMAKE_C_FLAGS_INIT by qnx-cmake's generated toolchain file, which
# is what makes this survive a reconfigure -- see the comment there.
CFLAGS:append = " -I${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}"

# This CMakeLists has no install() rules -- it only declares the executable --
# so unlike rpi-gpio there is nothing for OECMAKE_INSTALL_PREFIX to act on and
# the binary is placed by hand.
#
# /Motor_Data_Producer/ is not a conventional location, and it is deliberate:
# the reference guest image puts the binary and its config.json there together,
# and /scripts/start-guest1.sh invokes it by that absolute path. The same shape
# as /Motor_AI_Client/ next to it.
do_install() {
	install -d ${D}${QNX_STAGE_DIR}/motor-data-producer
	install -m 0755 ${B}/motor_data_producer ${D}${QNX_STAGE_DIR}/motor-data-producer/
	install -m 0644 ${S}/config.json         ${D}${QNX_STAGE_DIR}/motor-data-producer/

	# Headers for motor_ai_client, which compiles against them. Sysroot only --
	# they are a build-time contract and have no business in an image.
	install -d ${D}${QNX_STAGE_INCLUDEDIR}
	install -m 0644 ${S}/motor_wire.h ${D}${QNX_STAGE_INCLUDEDIR}/
	install -m 0644 ${S}/motor_shm.h  ${D}${QNX_STAGE_INCLUDEDIR}/
}

# Placed explicitly: /Motor_Data_Producer is not on any mkifs search path, so
# the automatic pass would only warn about both files.
QNX_IFS_AUTO_ENTRIES = "0"

QNX_IFS_EXTRA_ENTRIES = "\
/Motor_Data_Producer/motor_data_producer=@QNX_IFS_ROOT@/motor-data-producer/motor_data_producer\n\
/Motor_Data_Producer/config.json=@QNX_IFS_ROOT@/motor-data-producer/config.json\
"

# Not started from the boot script, matching the reference guest: it wants the
# SPI bus, and whether this guest or the host owns SPI0 is a runtime decision.
# /scripts/start-guest1.sh starts it, and that is run by hand.
