function main(args) {
    var cmd = "";
    for (var i = 0; i < args.length; i++) {
        cmd = cmd + args[i] + " ";
    }
    stdOut.write(bridge.req(cmd));
 
}
