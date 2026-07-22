SUMMARY = "CommonAPI/SOME/IP client for the QNX motor demo"
DESCRIPTION = "Guest-side client that talks to motor_ai_server over SOME/IP."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor_ai_client.git;protocol=ssh;branch=main"

# ---------------------------------------------------------------------------
# BLOCKED: needs a someip recipe, which does not exist yet.
# ---------------------------------------------------------------------------
# The build sources ${SOMEIP_DIR}/commonapi-qnx/scripts/env.sh, which provides
# the generated CommonAPI bindings, the vsomeip libraries and the code
# generators. someip is ~1.5G, still lives in the monorepo, and has no
# repository of its own, so there is nothing to DEPENDS on.
#
# Once it is packaged, this recipe needs:
#
#     DEPENDS = "someip"
#     EXTRA_OEMAKE = "SOMEIP_DIR='<where someip staged its tree>'"
#
# SOMEIP_DIR is already ?= in the Makefile, so it can be pointed anywhere.
#
# Deliberately not in any image's QNX_IFS_INSTALL until that exists: a recipe
# that cannot build should fail when you ask for it, not when you build a guest.
python () {
    raise bb.parse.SkipRecipe(
        "motor-ai-client needs a someip recipe (CommonAPI/vsomeip) that does "
        "not exist yet -- see the comment in this recipe")
}

do_compile() {
	oe_runmake -C ${S} SOMEIP_DIR='${SOMEIP_DIR}'
}

do_install() {
	install -d ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/Motor_AI_Client
	install -m 0755 ${S}/client/build/MotorDataClient \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/Motor_AI_Client/motor_ai_client
	install -m 0644 ${S}/client/vsomeip_multicast.json \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/Motor_AI_Client/vsomeip.json
}
