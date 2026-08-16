SUMMARY = "TUI for injecting a chosen motor fault into the diagnostics chain"
DESCRIPTION = "Raises a fault on demand so the head unit's fault card, severity \
ring and alert list can be exercised without a real defect. Writes the chosen \
verdict to /motor_fault_override, which motor_diag_service reads in preference \
to the AI's own region and publishes over SOME/IP to the IVI -- so a test drives \
the real string-to-wire mapping rather than a shortcut around it."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/fault_tester.git;protocol=ssh;branch=main"

# None. It links libc and nothing else -- the TUI is ANSI escapes and termios,
# which the QNX console, a serial line and an ssh session all handle. That is
# also why there is no curses dependency to find for this target.
DEPENDS = ""

# The upstream Makefile takes CC, CFLAGS and LDFLAGS and needs nothing else.
# -D_QNX_SOURCE is what exposes the POSIX shm and termios declarations this
# uses; without it the build fails on implicit declarations rather than at link.
EXTRA_OEMAKE = "\
    CC='${CC}' \
    CFLAGS='${CFLAGS} -O2 -Wall -Wextra -std=gnu11 -D_QNX_SOURCE' \
    LDFLAGS='${LDFLAGS}' \
"

do_compile() {
	oe_runmake -C ${S}
}

# Into the processor tree's usr/bin, which mkifs searches by bare name -- so
# unlike motor_ai_client this needs no explicit IFS record, and `fault_tester`
# is on PATH at the guest console.
do_install() {
	install -d ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin
	install -m 0755 ${S}/fault_tester \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin/fault_tester
}

# Not started at boot, and deliberately: it is an interactive tool that latches
# a fault until released. Something that injected a fault automatically at
# every boot would eventually be mistaken for a real one.
#
# Run it from the guest console or over ssh:
#
#     fault_tester
#
# One instance at a time -- the override region has a single writer by design,
# and two testers corrupt its seqlock.
