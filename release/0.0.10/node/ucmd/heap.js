function main(args) {
  var value = bridge.req("heap");
  stdOut.write(value);
}