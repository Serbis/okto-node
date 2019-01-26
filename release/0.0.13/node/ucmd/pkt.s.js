/** Exb transactons tester. Performs a transaction at the 'addr' address with the
 *  'cmd' command, with 'total' count number and print text visalization in 
 *  'perl' lines. A successful transaction is marked with a # symbol, failed by 
 *  the X symbol. At the end of the program, the program displays a report on
 *  the number of failed transactions and for what reasons. This program is used
 *  for two things:
 * 
 *  1. Testing the quality of the wireless link. With a large number of tranzac 
 *  (> 1000), this program provides a fairly objective assessment of the level 
 *   of interference in the air.
 * 
 *  2. Testing firmware for memory leaks and hidden bugs. Again with large 
 *  quantities, firmware with errors will not be able to reverse such a number
 *  of transactions.
 *
 *  Veriosn: 1
 */
function main(args) {
    var addr = 0xAAAAAA01;
    var cmd = "heap";
    var total = 900;
    var perl = 30;
    
    //-----
    
    var inl = 1;
    var bad1 = 0;
    var bad2 = 0;
    var bad3 = 0;
    var bad4 = 0;
    var bad5 = 0;
    var bad6 = 0;
    var bad7 = 0;
    var bad8 = 0;
    for (var i = 0; i < total; i++) {
        var r = bridge.req(addr, cmd);
          if(r.error()) {
                if (r.error() === 1) {
                    stdOut.write("X");
                    bad1++;
                } else if (r.error() === 2) {
                    stdOut.write("X");
                    bad2++;
                } else if (r.error() === 3) {
                    stdOut.write("X");
                    bad3++;
                } else if (r.error() === 4) {
                    stdOut.write("X");
                    bad4++;
                } else if (r.error() === 5) {
                    stdOut.write("X");
                    bad5++;
                } else if (r.error() === 6) {
                    stdOut.write("X");
                    bad6++;
                } else if (r.error() === 7) {
                    stdOut.write("X");
                    bad7++;
                } else {  
                    stdOut.write("X");
                    bad8++;
                }
          } else {
              stdOut.write("#");
          }
          if (inl === perl) {
            stdOut.write("\n");
            inl = 1;
          } else {
              inl++;
          }
    }
    
    var tloss = bad1 + bad2 + bad3 + bad4 + bad5 + bad6 + bad7 + bad8;
    stdOut.write("ExbError = " + bad1 + " / " + (bad1 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("ExbAddrNotDefined = " + bad2 + " / " + (bad2 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("ExbUnreachable = " + bad3 + " / " + (bad3 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("BridgeOverload = " + bad4 + " / " + (bad4 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("ExbBrokenResponse = " + bad5 + " / " + (bad5 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("TransactionTimeout = " + bad6 + " / " + (bad6 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("DriverError = " + bad7 + " / " + (bad7 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("UnknownError = " + bad8 + " / " + (bad8 / (total / 100)).toFixed(2) + " %\n");
    stdOut.write("Total loss = " + tloss + " / " + (tloss / (total / 100)).toFixed(2) + " %\n");

    
}