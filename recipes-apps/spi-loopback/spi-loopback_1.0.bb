SUMMARY = "SPI loopback test for QNX on the Raspberry Pi"
DESCRIPTION = "Writes a pattern over SPI and reads it back, for checking wiring \
and the driver before trusting real hardware. No dependencies beyond the SDP."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/spi_loopback.git;protocol=ssh;branch=main"

# The Makefile assigns CC itself and bakes the -V variant into its own CFLAGS,
# so only the compiler needs steering.
EXTRA_OEMAKE = "CC='${CC}'"

do_compile() {
	oe_runmake -C ${S}
}

do_install() {
	install -d ${D}${QNX_STAGE_BINDIR}
	install -m 0755 ${S}/spi_loopback ${D}${QNX_STAGE_BINDIR}/spi_loopback
}

# /proc/boot, which is where the reference guest has it -- a bare destination is
# how mkifs spells that, and the reference writes it the same way. Staged into
# the stage tree's bin/ regardless, since that is what the harvesting pass reads.
QNX_IFS_DEST[spi_loopback] = "spi_loopback"
