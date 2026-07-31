SUMMARY = "Shared-memory chunker/sender for the QNX motor demo"
DESCRIPTION = "First real application ported from the QNX hypervisor project's \
makefile build (src/shm_sender). Its Makefile already cross-compiles correctly \
for QNX, so this recipe drives it as-is rather than reimplementing the build: \
what Yocto adds is the environment, the staging, and the image dependency."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

# Its own repository, split out of the hypervisor monorepo. Fetched rather than
# built in place, so this recipe has a revision to hash and therefore sstate --
# the working-tree build it replaced had neither and rebuilt every time.
#
# The repository root is the application: what used to be src/shm_sender inside the
# monorepo. If the split kept that nesting instead, add it back with
# QNX_SRC_SUBDIR = "src/shm_sender".
#
# QNX_SRC_REV defaults to ${AUTOREV}, which needs the network at *parse* time --
# every bitbake invocation, not just a fetch. Pin it for reproducible and offline
# builds:
#
#     QNX_SRC_REV = "<commit sha>"
QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/shm-chunker.git;protocol=ssh;branch=main"

# The upstream Makefile hardcodes CC := qcc / CXX := q++ and builds the -V
# variant into its own CFLAGS. Passing CC/CXX on the command line overrides the
# makefile's assignment (make gives command-line variables precedence), which is
# what routes the build through the compiler this class configured.
#
# CFLAGS is deliberately NOT passed: the Makefile uses simple assignment, so
# overriding it would drop its -std and -V flags along with everything else.
EXTRA_OEMAKE = "CC='${CC}' CXX='${CXX}'"

# The Makefile writes into a build/ directory beside itself. That used to be
# implicit: building the working tree in place made EXTERNALSRC_BUILD default to
# <source>/build, so ${B} already pointed there. Fetching leaves B equal to S,
# and do_install then looked for the binary one directory too high:
#
#     install: cannot stat '.../shm-chunker/1.0+git/git/shm_chunker'
#
# The same fix motor-controller carries, for the same reason. It holds for both
# paths: under externalsrc S is the checkout, so ${S}/build is exactly the
# EXTERNALSRC_BUILD default this used to rely on.
B = "${S}/build"

# Its check-env target refuses to run without these, which this class exports.
do_compile() {
	oe_runmake -C ${S}
}

do_install() {
	install -d ${D}${QNX_STAGE_BINDIR}
	install -m 0755 ${B}/shm_chunker ${D}${QNX_STAGE_BINDIR}/shm_chunker
}
