/** Exb command transactor. This program is used to send direct commands to exb.
 *  In the first arg, the address at which the request will occur is 
 *  indicated, and the command itself is in the subsequent ones. For example:
 * 
 *  exb.s AAAAAA01 gpio_w 0 1 
 * 
 *  or for uart:
 * 
 *  exb.s 0 gpio_w 0 1
 * 
 * The command gives either a result or a determination of the cause, what went
 * wrong
 * 
 * Version: 2
 */ 
function main(args) {
    var addr = parseInt(args[0], 16);
    var cmd = "";
    for (var i = 1; i < args.length; i++) {
        cmd = cmd + args[i] + " ";
    }
    var r = bridge.req(addr, cmd);
          if(r.error()) {
                if (r.error() === 1)
                    stdOut.write("ERROR: ExbError <" + r.result() + ">\n");
                else if (r.error() === 2) 
                    stdOut.write("ERROR: ExbAddrNotDefined\n");
                else if (r.error() === 3) 
                    stdOut.write("ERROR: ExbUnreachable\n");
                else if (r.error() === 4) 
                    stdOut.write("ERROR: BridgeOverload\n");
                else if (r.error() === 5) 
                    stdOut.write("ERROR: ExbBrokenResponse\n");
                else if (r.error() === 6) 
                    stdOut.write("ERROR: TransactionTimeout\n");
                else if (r.error() === 7) 
                    stdOut.write("ERROR: DriverError\n");
                else  
                    stdOut.write("ERROR: UnknownError" + r.error() + "\n");
          } else {
              stdOut.write("RESULT: " + r.result() + "\n");
          }
}