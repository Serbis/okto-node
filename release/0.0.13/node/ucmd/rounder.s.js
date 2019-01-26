import { melt as melt } from "telema.lib";

//TODO все таки первый раунд с ошибкой происходит, это хорошо видно на графике

function main(args) {
    if (args.length < 6) {
        exit("Must be 6 args but found " + args.length, 10);
        return;
    }
    var start = parseInt(args[0]);
    if (isNaN(start)) {
        exit("First arg 'start' must be a number, but found " + args[0], 10);
        return;
    }
    var end = parseInt(args[1]);
    if (isNaN(end)) {
        exit("Second arg 'end' must be a number, but found " + args[1], 10);
        return;
    }
    var round = parseInt(args[2]);
    if (isNaN(round)) {
        exit("Third arg 'round' must be a number, but found " + args[2], 10);
        return;
    }
    var stratagy = args[3];
    if (stratagy !== "min"
        && stratagy !== "max"
        && stratagy !== "avg"
        && stratagy !== "wavg"
        && stratagy !== "first"
        && stratagy !== "last") {

        exit("Fourth arg 'stratagy' define unkown type " + args[3], 10);
        return;
    }

    rounder(start, end, round, stratagy, args[4], args[5]);
}

function rounder(start, end, round, stratagy, dir, ns) {
    var journal = new melt.Melt(dir, ns).load();
    if (journal.error) {
        exit("Unable to load journal be error " + journal.error, 11);
        return;
    }

    var iterator = journal.read(start);
    if (iterator.error) {
        exit("Unable to read journal be error " + iterator.error, 12);
        return;
    }

    var rLow;
    var rUp;
    var fiter = true;
    var rCollector = [];

    while (true) {
        if (!iterator.hasNext()) {
            if (rCollector.length > 0)
                stdOut.write(toCsv(aveRound(rCollector, stratagy)));
            break;
        }

        var data = iterator.next().split(",");
        var dts = parseInt(data[1]);

        //If first iteration, initizlize zero roud
        if (fiter) {
            rLow = dts;
            rUp = rLow + round;
            fiter = false;
        }

        if (dts < rUp) {
            rCollector.push([data[0], dts]);
        } else {
            rLow = dts;
            rUp = rLow + round;
            rCollector.push([data[0], dts]);
            stdOut.write(toCsv(aveRound(rCollector, stratagy)));
            rCollector = [];
        }
    }

    iterator.close();
    stdOut.write("-");
}

function aveRound(collector, stratagy) {
    //stdOut.write("COLLECTED=" + collector.length + "\n");

    switch(stratagy) {
        case "min": return stratMin(collector);
        case "max": return stratMax(collector);
        case "avg": return stratAvg(collector);
        case "wavg": return stratWavg(collector);
        case "first": return stratFirst(collector);
        case "last": return stratLast(collector);

    }

}

function stratMin(collector) {
    var minTs = findMinTimestamp();
    return [collector[0][0], minTs];
}

function stratMax(collector) {

}

function stratAvg(collector) {

}

function stratWavg(collector) {

}

function stratFirst(collector) {
    var minTs = findMinTimestamp(collector);
    return [collector[0][0], minTs];
}

function stratLast(collector) {

}

function findMinTimestamp(collector) {
    var minTs = java.lang.Long.MAX_VALUE;
    for (var i = 0; i < collector.length; i++) {
        var s = collector[i][1];
        if (s < minTs)
            minTs = s;
    }

    return minTs;
}

function toCsv(arr) {
    return arr[0] + "," + arr[1] + "\n";
}

function exit(msg, code) {
    stdOut.write(msg);
    program.exit(code);
}