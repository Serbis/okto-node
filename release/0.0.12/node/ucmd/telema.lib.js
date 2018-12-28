/** Telemetry Library 
 * Version = 1 */
 
_export melt

    /** Telemetry Journal. The object implements telemetry logging functions in 
     *  the form of a temporal database. The following public API is available:
     * 
     *  constructor(dir, ns) - create the new journal that will operate with the
     *      specified dir in the specified namespace
     * 
     *  createIfNotExist(splitRange, maxRanges) - if the journal with specified
     *      ns and dir does not exist, initialize the new jounal. splitRange
     *      determine file fragmentation in ms and maxRange determine max
     *      range files in the journal
     * 
     *  write(value) - write the value to the jounal
     * 
     *  read(timestamp) - read journal from the specified timestamp. This call
     *      return interator with standard next and hasNext methods
     * 
     */
    Melt: function(dir, ns) {
        this.meta = {
            splitRange: 86400000, //1 file each 1D 
            maxRanges: 10, //10 files
            ranges: []
        }; 
        
        this.error = 0;
        this.curFile = null;
        this.curUpBound = null;
        
        //------------------------------------------
        
        var ReadIterator = function(timestamp, meta, mel) {
            this.error = 0;
            this.nextValue = null;
            
            //Find file with with searched timestamp
           
            this.curRange = null;
            var self = this;
            meta.ranges.forEach(function(v) {
                if (timestamp >= v.start && timestamp <= v.end) {
                    self.curRange = v;
                }
            });
            
            //If range does not found it means that timestamp is under all ranges
            //and must be selectem lowest range
            if (this.curRange === null) {
                var min = java.lang.Long.MAX_VALUE;
                for (var i = 0; i < meta.ranges.length; i++) {
                    var s = meta.ranges[i].start;
                    if (s < min) {
                        min = s;
                        this.curRange = meta.ranges[i];
                    }
                }
            }
            
            //Open file and iterate to first value that date > star
            this.fit = storage.readStreamSep(dir + "/" + this.curRange.file, "\n");
            if (this.fit === null) {
                 //If start file coud not be openend set error 1.
                this.error = 1; 
                return;
            }
            while (true) {
                if (!this.fit.hasNext()) {
                    //If searched range not found in the target file set error 2.
                    //This indicates damage to the jounal structure
                    this.error = 2; 
                    return;
                }
                
                var value = this.fit.next();
                var csv = value.split(",");
                if (csv[1] >= timestamp) {
                    this.nextValue = value;
                    break;
                }
            }
            
            
            this.hasNext = function() {
                return this.nextValue !== null;
            };
            
            this.next = function() {
                var n = this.nextValue;
                if (this.fit.hasNext()) {
                    this.nextValue = this.fit.next();
                } else { 
                    //Find nearest range to furure relative current range
                    var near = mel.__findUpRange().start;
                    var rn = null;
                    for (var i = 0; i < meta.ranges.length; i++) {
                        var s = meta.ranges[i].start;
                        //stdOut.write("near=" + near + "\n");
                        //stdOut.write("s=" + s + "\n");
                        //stdOut.write("this.curRange.start=" + this.curRange.start + "\n");
                        if (s <= near && s > this.curRange.start) {
                            near = s;
                            rn = meta.ranges[i];
                        }
                    }
                    //stdOut.write("NEAR" + rn + "\n")
                    //If current range is not last
                    if (near !== this.curRange.start) {
                        this.curRange = rn;
                        this.fit = storage.readStreamSep(dir + "/" + this.curRange.file, "\n");
                        if (this.fit.hasNext()) {
                            this.nextValue = this.fit.next();
                        } else {
                            this.nextValue = null;
                        }
                    } else {
                        this.nextValue = null;
                    }
                }
                    
                return n;
            };
        };
        
        //------------------------------------------
        
       /**
        * Create mel structire if it does not exist
        * 
        * @param splitRange dates range in one file
        * @param maxRanges maximum range files
        */
        this.createIfNotExist = function(splitRange, maxRanges) {
           var mf = storage.read(dir + "/" + ns + ".mel");
            if (mf === null) {
                this.meta.splitRange = splitRange;
                this.meta.maxRanges = maxRanges;
                this.__rewriteMeta();
                storage.write(dir + "/" + ns + ".rl", "0");
                storage.write(dir + "/" + ns + ".wl", "0");
            }
            
            return this;
        };
       
       /**
        * Load meta structure and initialize mel for work 
        */
        this.load = function() {
            var mf = storage.read(dir + "/" + ns + ".mel");
            if (mf === null) {
                this.error = 1;
            } else {
                this.meta = JSON.parse(mf);
            }
             return this;
        };
        
        /**
         * Write vlue to the file
         */ 
        this.write = function(value) {
            var timestamp = new Date().getTime();
            if (this.curFile === null) {
                if (this.meta.ranges.length === 0) {
                    var cd = timestamp;
                    var up = cd + this.meta.splitRange;
                    var fn = ns + "_" + cd + ".m";
                    this.meta.ranges.push({
                        start: cd,
                        end: up,
                        file: fn
                    });
                    this.__rewriteMeta();
                    this.curFile = fn;
                    this.curUpBound = up;
                } else {
                    var mr = this.__findUpRange();
                    this.curFile = mr.file;
                    this.curUpBound = mr.end;
                }
            }
            
            //If current date is out of the current work range - create new range
            if (timestamp > this.curUpBound) {
                var cd1 = timestamp;
                var up1 = cd1 + this.meta.splitRange;
                var fn1 = ns + "_" + cd1 + ".m";
                this.meta.ranges.push({
                    start: cd1,
                    end: up1,
                    file: fn1
                });
                
                //If rages cout out of the max ranges count - remove younger range
                //and it file
                if (this.meta.ranges.length > this.meta.maxRanges) {
                    var min = java.lang.Long.MAX_VALUE;
                    for (var i = 0; i < this.meta.ranges.length; i++) {
                        var s = this.meta.ranges[i].start;
                        if (s < min) {
                            min = s;
                        }
                    }
                    this.meta.ranges = this.meta.ranges.filter(function (v) {
                        return v.start !== min;
                    });
                    storage.delete(dir + "/" + ns + "_" + min + ".m");
                } 
            
                this.__rewriteMeta();
                this.curFile = fn1;
                this.curUpBound = up1;
                this.write(value);
            } else {
                storage.append(dir + "/" + this.curFile, value + "," + timestamp + "\n");
            }
        };
      
        this.read = function(timestamp) {
          return new ReadIterator(timestamp, this.meta, this);
        };
        
        /**
         * Persist meta object
         */ 
        this.__rewriteMeta = function() {
            storage.write(dir + "/" + ns + ".mel", JSON.stringify(this.meta));
        };
        
        /**
         * Search upper range in the meta object
         */ 
        this.__findUpRange = function() {
            var max = 0;
            var mr = null;
            for (var i = 0; i < this.meta.ranges.length; i++) {
                var s = this.meta.ranges[i].start;
                if (s > max) {
                    max = s;
                    mr = this.meta.ranges[i];
                }
            }
            
            return mr;
        };
    }
export_
