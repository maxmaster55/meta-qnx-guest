SUMMARY = "SOME/IP diagnostics publisher: motor faults and raw captures to the head unit"
DESCRIPTION = "Runs on the QNX guest and serves the AAOS head unit over Ethernet. \
Publishes a 1 Hz fault classification derived from the AI pipeline's verdicts in \
/motor_ai_result, and answers capture requests with 10 s of the 12 raw signal \
channels taken from motor_data_producer's /motor_ctrl ring. Raw SOME/IP rather \
than CommonAPI, because the head unit has neither vsomeip nor Boost and the two \
sides meet at the wire."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor_diag_service.git;protocol=ssh;branch=main"

# motor_wire.h and motor_shm.h -- the shared-memory layout it reads. Taken from
# the producer rather than vendored, because compiling against a different copy
# than the producer was built with is a silent wrong answer rather than a link
# error.
DEPENDS = "motor-data-producer"

# LDLIBS is spelled out because the upstream default (-lsocket) is right for
# QNX but the class does not supply it, and MOTOR_HEADERS is the one path
# upstream leaves to the caller.
EXTRA_OEMAKE = "\
    CC='${CC}' \
    CFLAGS='${CFLAGS} -O2 -Wall -Wextra -std=gnu11 -D_QNX_SOURCE' \
    LDFLAGS='${LDFLAGS}' \
    LDLIBS='-lsocket' \
    MOTOR_HEADERS='${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}' \
"
EXTRA_OEMAKE[vardepsexclude] = "RECIPE_SYSROOT"

do_compile() {
	oe_runmake -C ${S}
	# The head-unit stand-in. Small, and the only way to test the whole
	# path -- discovery, subscribe, events, capture -- without an Android
	# device on the bench.
	oe_runmake -C ${S} probe
}

do_install() {
	install -d ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin
	install -m 0755 ${S}/motor_diag_service \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin/motor_diag_service
	install -m 0755 ${S}/diag_probe \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin/diag_probe
}

# Both land in the processor tree's usr/bin, which mkifs searches by bare name,
# so neither needs an explicit IFS record and both are on PATH at the console.
#
# Started from /scripts/start-guest1.sh, because the address it advertises in
# its SD offer has to be the guest's address on the head unit's wire -- which
# the boot script is where that is known.
