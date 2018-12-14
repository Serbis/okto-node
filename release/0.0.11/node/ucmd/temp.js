var table = [[3975, -50],
[3937, -45],
[3890, -40],
[3830, -35],
[3758, -30],
[3671, -25],
[3569, -20],
[3450, -15],
[3315, -10],
[3163, -5],
[2997, 0],
[2818, 5],
[2631, 10],
[2436, 15],
[2242, 20],
[2048, 25],
[1859, 30],
[1678, 35],
[1508, 40],
[1349, 45],
[1204, 50],
[1070, 55],
[950, 60],
[842, 65],
[747, 70],
[661, 75],
[586, 80],
[519, 85],
[460, 90],
[409, 65],
[363, 100],
[323, 105],
[288, 110]];

function getTemp(voltage) {
	for (i = 0; i < table.length; i++) {
		if (table[i + 1][0] < voltage && table[i][0] >= voltage) {
			return interpolate(table[i + 1][0], table[i][0], table[i][1], table[i + 1][1], voltage);
		}
	}
}

function interpolate(x1, x2, y1, y2, x) {
	return y1 + ((y2 - y1) / ((x2 - x1) / (x2 - x)));
}

function main(args) {
	var value = parseInt(bridge.req("readAdc 8"));
	if (value !== -1) {
        var fin = getTemp(value);
        stdOut.write(fin.toFixed(2) + " C");
    } else {
        stdOut.write("-1");
    }
}