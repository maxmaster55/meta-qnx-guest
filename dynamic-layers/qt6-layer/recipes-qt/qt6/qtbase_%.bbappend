# qtbase for the hypervisor guest: build it with OpenGL ES 2.
#
# meta-qnx defaults QNX_QTBASE_OPENGL to "no" because whether a GL stack exists
# is a property of the board, and it cannot know. This guest is a board that
# has one, so the answer belongs here rather than there.
#
# What is behind it: qnx-screen-virtio ships EGL-mesa.so, GLESv2-mesa.so and
# screen-alloc-virtio-virgl.so, and vdev-virtio-gpu on the host turns the
# guest's command stream into real GPU work through virglrenderer. That package
# is already in QNX_ROOTFS_INSTALL, so the driver is on the data partition
# beside the application that needs it.
#
# The visible reason to bother: with no GL, Qt Quick has to run
# QT_QUICK_BACKEND=software, and the software backend has no shader support --
# every MultiEffect and layer.enabled item draws nothing at all, silently. The
# cluster's bezel, ring glow, status bands and telltales are all built from
# them, so the whole design disappears with no warning printed.
QNX_QTBASE_OPENGL = "es2"
