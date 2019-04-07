/** Runtime utils library
 * Version = 1 */
 
_export stop
    /** Check if process has signal 9 and return true if is is or false if not */
    mustStop: function () {
        if (runtime.hasSig()) {
            if (runtime.sig() === 9) 
                return true;
        }
    
        return false;
    },
    
    /** Send the message to the stdOut and set script complete code. If msg is 
     * not defined, whitespce will be send to the stdOut */
    hotStop: function(code, msg) {
        if (msg)
            stdOut.write(msg);
        else
            stdOut.write(" ");
            
        program.exit(code);
    }
    
export_
