SUMMARY = "Records the motor shared-memory ring to CSV and publishes over MQTT"
DESCRIPTION = "The third consumer of the motor controller's ring buffer, \
alongside motor_ai_client and shm_chunker. It writes rows to CSV files under a \
save directory and publishes status, data and download topics to an MQTT \
broker, taking commands back on a command topic."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

# Its own repository, split out of the hypervisor monorepo (src/motor_recorder).
#
# QNX_SRC_REV defaults to ${AUTOREV}, which needs the network at *parse* time --
# every bitbake invocation, not just a fetch. Pin it for reproducible and
# offline builds:
#
#     QNX_SRC_REV = "<commit sha>"
QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor-recorder.git;protocol=ssh;branch=main"

# PLACEHOLDER -- replace with the repository's first commit.
#
# It is pinned rather than left at the ${AUTOREV} default for a reason that
# matters before the repository exists: AUTOREV makes bitbake `git ls-remote`
# the repository at *parse* time, on every invocation. Against a repository that
# is not there yet that is a parse error, and a parse error in one recipe halts
# parsing for the whole tree -- so an unfetchable recipe sitting in a layer would
# stop `bitbake qnx-host-disk` from building anything at all.
#
# With a fixed revision nothing is fetched until this recipe is actually built,
# and the failure is then this recipe's alone and says exactly what is wrong.
QNX_SRC_REV = "0000000000000000000000000000000000000000"

# mosquitto is libmosquitto, built by this layer -- there is no MQTT client in
# the SDP and none in QNX's OSS repository either.
#
# motor-data-producer is not a runtime dependency, it is where motor_wire.h and
# motor_shm.h come from: that recipe stages both into QNX_STAGE_INCLUDEDIR, and
# they describe the shared-memory layout this reads. Compiling against a
# different copy of those headers than the producer was built with is a silent
# wrong answer rather than a link error, which is why they are taken from the
# producer rather than vendored here.
DEPENDS = "mosquitto motor-data-producer"

# The upstream Makefile cannot be driven, unlike shm-chunker's and
# motor-controller's, and the reason is worth stating so nobody tries again:
#
#     qnx:
#         @bash -c 'set -e; . "$(QNX800_DIR)/qnxsdp-env.sh"; \
#             "$$QNX_HOST/usr/bin/qcc" ...'
#
# It sources the SDP environment itself, from ../../qnx800 -- a path that only
# exists inside the monorepo -- and then calls qcc through the $QNX_HOST that
# sourcing produced. Under Yocto the environment is already set up by
# qnx-sdp.bbclass, pointing at QNX_SDP_ROOT, and there is nothing at ../../qnx800
# to source. Overriding CC would not help: the makefile never uses it.
#
# So the compile is spelled out here. It is one command, and it is the same one
# the makefile runs once its two monorepo-relative paths are removed.
#
# mqtt_minimal.c is deliberately absent, and that is not an oversight: the
# upstream makefile builds recorder.c and mqtt_client.c only. mqtt_minimal.c is
# a second, unused implementation of the same thing -- adding it gives duplicate
# symbols, not more features.
#
# ${CFLAGS} and ${LDFLAGS} rather than the flags written out: qnx-sdp.bbclass
# appends QNX_SYSROOT_CPPFLAGS and QNX_SYSROOT_LDFLAGS to them, which is what
# points at the staged mosquitto headers and library. Only the two flags the
# application actually needs on top -- its C standard and its POSIX level -- are
# named here.
do_compile() {
	cd ${S}
	${CC} ${CFLAGS} -std=c11 -D_POSIX_C_SOURCE=200809L \
		-o motor_recorder recorder.c mqtt_client.c \
		${LDFLAGS} -lmosquitto -lsocket -lm
}

# -lcjson is not here, though the upstream makefile passes it. Nothing in this
# application calls cJSON: the one JSON document it produces is built with
# snprintf into a char[4096] in recorder.c. The flag is vestigial, and carrying
# it would mean carrying a whole library into the image to satisfy a link line
# that has nothing to link.

do_install() {
	install -d ${D}${QNX_STAGE_BINDIR}
	install -m 0755 ${S}/motor_recorder ${D}${QNX_STAGE_BINDIR}/motor_recorder
}

# The broker address is a compile-time constant -- MQTT_BROKER in mqtt_client.h,
# currently a public IP -- so pointing this at a different broker is a change to
# the application, not to this recipe or to anything on the board. If that
# becomes inconvenient, the fix belongs upstream: read it from argv or a config
# file, as motor-data-producer does with its config.json.
#
# The save directory *is* an argument: -d <dir>, or the first non-flag argument,
# defaulting to /tmp. On this guest /tmp is RAM, so recordings meant to survive
# a reboot want a path on the mounted data disk instead.
