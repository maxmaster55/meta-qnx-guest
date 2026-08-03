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

QNX_SRC_REV = "41f047ee4dd965ce780e9d96a5cd0922d49c4581"

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

# The three paths the upstream Makefile leaves to the caller, because none of
# them is inside its repository. Everything else it needs -- the compiler, the
# optimisation and warning flags -- it takes from CC and CFLAGS, which this
# class already sets, so passing those is enough to drive it as-is.
#
# MOTOR_HEADERS and MQTT_INCDIR are the same directory here: motor-data-producer
# stages motor_wire.h and motor_shm.h into QNX_STAGE_INCLUDEDIR, and mosquitto
# stages mosquitto.h into the same place. They stay two variables because
# upstream has no reason to assume that.
EXTRA_OEMAKE = "\
    CC='${CC}' \
    MOTOR_HEADERS='${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}' \
    MQTT_INCDIR='${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}' \
    MQTT_LIBDIR='${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR}' \
"
EXTRA_OEMAKE[vardepsexclude] = "RECIPE_SYSROOT"

do_compile() {
	oe_runmake -C ${S}
}

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
