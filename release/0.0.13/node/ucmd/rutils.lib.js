/** Runtime utils library
 * Version = 1 */

_export stop
/** Check if process has signal 9 and return true if is is or false if not*/
mustStop: function () {
    if (runtime.hasSig()) {
        if (runtime.sig() === 9)
            return true;
    }

    return false;
}

export_
