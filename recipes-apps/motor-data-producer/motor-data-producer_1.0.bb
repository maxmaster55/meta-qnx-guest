SUMMARY = "Wire format and shared-memory layout for the motor data producer"
DESCRIPTION = "The QNX-SPI side of the STM32 motor data producer. Only its \
headers are staged: they define the wire format and the shared-memory layout \
that motor_ai_client reads, and are the contract between the two."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

QNX_SRC_REPO = "git://github.com/Mintharah/SPI-Stm32-QNX.git;protocol=https;branch=spi_qnx_build"

# Headers only, deliberately. The QNX-SPI directory also carries a
# motor_controller that overlaps with the giga_spi_8adc application already
# packaged as motor-controller; building both would put two similar binaries in
# an image with no way to tell which is which.
do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
	install -d ${D}${QNX_STAGE_INCLUDEDIR}
	install -m 0644 ${S}/QNX-SPI/motor_wire.h ${D}${QNX_STAGE_INCLUDEDIR}/
	install -m 0644 ${S}/QNX-SPI/motor_shm.h  ${D}${QNX_STAGE_INCLUDEDIR}/
}
