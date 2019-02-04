/* Adc utils functions
 * Version = 2
 */
 
_export readf

    /* Attempts to get the Int from the specified ADC input for the 
     * specified number of attempts with sleep defined in interval. If
     * operation was completed, s will be called with result value and value
     * returned, if not f will be called and null returned. 
     */
    readInt: function (addr, inp, attempts, interval, s, f) {
        var i = 0;
        while (i < attempts) {
            var v = bridge.req(addr, "adc_r " + inp);
            if (!v.error()) {
                var n = parseInt(v.result());
                if (s)
                    s(n);
                return n;
            }
            
            
            if (interval) {
                if (interval > 0)
                    runtime.sleepc(interval);
            }
            i++;
        }
        
        if (f)
            f();
        return null;
    }
    
export_
