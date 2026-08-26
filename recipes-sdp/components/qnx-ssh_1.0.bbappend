# qnx-ssh_1.0.bb, in meta-qnx, is one recipe shared by every image that
# installs it -- its QNX_SSH_USE_PAM/QNX_SSH_STRICT_MODES ${...} expansions
# resolve against THAT recipe's own datastore, not the image's. Setting them
# inside qnx-guest-image_1.0.bb (as this project used to) lands in the image
# recipe's datastore instead and never reaches this one -- silently: no
# error, no warning, just a guest that keeps shipping UsePAM yes/StrictModes
# yes no matter what the image recipe says. qnx-ssh_1.0.bb's own comment on
# QNX_SSH_AUTHORIZED_KEYS documents the identical mistake for a different
# variable pair. A .bbappend is the actual mechanism for one layer to reach
# into another recipe's datastore -- this is that.
#
# The guest-specific reasoning for turning both off lives in
# qnx-guest-image_1.0.bb, next to the rest of this guest's ssh setup, not
# here: this file only has to get the two values into the right datastore.
QNX_SSH_USE_PAM = "no"
QNX_SSH_STRICT_MODES = "no"
