function main(args) {
    var value = bridge.req("readAdc 6");
    if (value !== -1) {
        var fin = value / 4096 * 100;
        stdOut.write(fin.toFixed(2) + " %");
    } else {
        stdOut.write("-1");
    }
}