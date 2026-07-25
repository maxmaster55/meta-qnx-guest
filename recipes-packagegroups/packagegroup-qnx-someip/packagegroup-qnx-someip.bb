SUMMARY = "SOME/IP runtime libraries"
DESCRIPTION = "vsomeip and the CommonAPI runtimes, with the boost they link \
against. An image carrying a SOME/IP application needs all four: a binary with \
motor_ai_client in it but no libCommonAPI.so is one that cannot load. Grouping \
them means an image asks for the runtime rather than reciting its members, and \
a future component added to the runtime reaches every image that uses it."
LICENSE = "CLOSED"

inherit qnx-packagegroup

QNX_PACKAGEGROUP_INSTALL = "vsomeip commonapi-core commonapi-someip boost"
