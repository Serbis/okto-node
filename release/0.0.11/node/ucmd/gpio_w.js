function main(args) {
  stdOut.write(bridge.req("gpio_w " + args[0] + " " + args[1]));
}