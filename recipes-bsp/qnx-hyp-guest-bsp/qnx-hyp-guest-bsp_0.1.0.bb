SUMMARY = "Hypervisor guest board support package, from the SDP"
DESCRIPTION = "shmem-guest and wdtkick -- the two binaries that let a guest use \
the shmem and wdt-sp805 vdevs qvm offers it. Like the board BSPs, this is \
delivered as a zip under ${QNX_SDP_ROOT}/bsp rather than installed into \
${QNX_TARGET}, so mkifs cannot see it until something unpacks it."
LICENSE = "CLOSED"

inherit qnx-bsp

QNX_SDP_REQUIRES = "com.qnx.qnx800.bsp.hyp.guest"

# The ARM guest BSP. There is an x86 one beside it in the same directory, which
# this glob deliberately does not match.
QNX_BSP_ZIP_GLOB = "BSP_hyp-guest-arm_*.zip"

# What the archive carries -- four files, of which an image wants the first two:
#
#   aarch64le/sbin/shmem-guest         the shmem vdev demo utility
#   aarch64le/sbin/wdtkick             the watchdog kicker
#   aarch64le/boot/sys/startup-armv8_fm  already in ${QNX_TARGET}; the copy here
#                                        is the same program and is not used,
#                                        since the boot line resolves it from
#                                        the SDP like every other image does
#   aarch64le/usr/lib/libstartup.a     dropped by the class's QNX_BSP_EXCLUDE --
#                                      it is for linking a startup program, and
#                                      the board BSP ships its own copy at the
#                                      same path, so staging both is a conflict
#                                      with nothing on either side that wants it
#
# Everything else is staged as-is: pruning further would mean this recipe having
# an opinion about which binaries an image may use.
