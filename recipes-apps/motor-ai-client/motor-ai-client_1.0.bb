SUMMARY = "CommonAPI/SOME/IP motor data client"
DESCRIPTION = "Guest-side client that talks to motor_ai_server over SOME/IP. Its \
CMakeLists runs the CommonAPI generators at configure time to turn the \
.fidl/.fdepl interface definitions into C++ bindings, then builds them alongside \
the client."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-src

QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor_ai_client.git;protocol=ssh;branch=main"

# The runtimes it links against, and the generators it runs. The generators are
# native: x86_64 host tools that produce source, not target binaries.
DEPENDS = "commonapi-someip commonapi-core vsomeip boost commonapi-generators-native \
           motor-data-producer"

# The QNX byte-order and narrowing settings the whole SOME/IP stack needs; the
# generated CommonAPI code pulls in the same headers the runtimes do.
require recipes-someip/someip-qnx-flags.inc

# The client lives in client/, but its CMakeLists reaches ../interface for the
# .fidl definitions, so the repository root has to be the source directory.
OECMAKE_SOURCEPATH = "${S}/client"

# Upstream looks for its dependencies under one output directory: lib/ for the
# libraries and generators/{core,someip}/ for the code generators. Neither
# exists here -- the libraries are in the sysroot and the generators are native
# tools on PATH -- so a directory of that shape is assembled from both.
QNX_SOMEIP_SHIM = "${WORKDIR}/someip-shim"

export LIBS_DIR = "${QNX_SOMEIP_SHIM}"
export OUTPUT_DIR = "${QNX_SOMEIP_SHIM}"

do_configure:prepend() {
	install -d ${QNX_SOMEIP_SHIM}/generators
	ln -sfn ${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR} ${QNX_SOMEIP_SHIM}/lib
	ln -sfn ${STAGING_DATADIR_NATIVE}/commonapi-generators/core   ${QNX_SOMEIP_SHIM}/generators/core
	ln -sfn ${STAGING_DATADIR_NATIVE}/commonapi-generators/someip ${QNX_SOMEIP_SHIM}/generators/someip
}

# These land in /Motor_AI_Client and /etc, neither of which mkifs searches by
# bare name, so the automatic pass is turned off and the records are written
# out. The destinations match the project's own guest build files.
QNX_IFS_AUTO_ENTRIES = "0"

QNX_MOTOR_AI_DIR = "${QNX_STAGE_DIR}/motor-ai-client"

do_install() {
	install -d ${D}${QNX_MOTOR_AI_DIR}
	install -m 0755 ${B}/MotorDataClient ${D}${QNX_MOTOR_AI_DIR}/motor_ai_client
	install -m 0644 ${S}/client/vsomeip_multicast.json ${D}${QNX_MOTOR_AI_DIR}/vsomeip.json
	install -m 0644 ${S}/interface/commonapi4someip.ini ${D}${QNX_MOTOR_AI_DIR}/commonapi.ini

	# window_rows, the call timeout and the shm poll interval. The client
	# looks for it at /etc/motor-ai-client/client.conf with no environment
	# variable set, which is where the IFS record below puts it, so the
	# guest boot script needs no change to make it take effect.
	install -m 0644 ${S}/client/client.conf ${D}${QNX_MOTOR_AI_DIR}/client.conf
}

# @QNX_IFS_ROOT@ is expanded by the image recipe, since the path depends on
# which image installs this.
#
# /etc/commonapi.ini is +dupignore because the client and the server both ship
# it. In the project they live in different guests so it never collided; here
# they can share an image, and mkifs treats a redefined entry as an error. The
# file is the same CommonAPI binding configuration either way.
QNX_IFS_EXTRA_ENTRIES = "\
/Motor_AI_Client/motor_ai_client=@QNX_IFS_ROOT@/motor-ai-client/motor_ai_client\n\
/Motor_AI_Client/vsomeip.json=@QNX_IFS_ROOT@/motor-ai-client/vsomeip.json\n\
/etc/motor-ai-client/client.conf=@QNX_IFS_ROOT@/motor-ai-client/client.conf\n\
[+dupignore] /etc/commonapi.ini=@QNX_IFS_ROOT@/motor-ai-client/commonapi.ini\n\
[+dupignore] /etc/commonapi4someip.ini=@QNX_IFS_ROOT@/motor-ai-client/commonapi.ini\
"

# Started at boot by /scripts/start-guest1.sh, which qnx-guest-image writes into
# the IFS -- not by hand, which is what the reference guest and an older version
# of this comment did. The script runs it under an `until` loop so a failure
# restarts it, which is the QNX-side equivalent of the Restart=on-failure the
# Linux half gets from systemd; it matters at boot, because the client gives
# motor_data_producer 10 seconds to create /motor_ctrl and exits if it never
# arrives.
#
# It reads /etc/motor-ai-client/client.conf, placed by the IFS record above.
# That is the client's own default path, so nothing in the boot script points
# at it and changing the window size means editing that file, not the image.
