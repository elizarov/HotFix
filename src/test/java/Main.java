// Fastest dfs by aman28rwt from https://codeforces.com/problemset/status/1088/problem/E?order=BY_CONSUMED_TIME_ASC

public class Main implements Runnable {
    int[][] packU(int n, int[] from, int[] to) {
        int[][] g = new int[n][];
        int[] p = new int[n];
        for (int f : from)
            p[f]++;
        for (int t : to)
            p[t]++;
        for (int i = 0; i < n; i++)
            g[i] = new int[p[i]];
        for (int i = 0; i < from.length; i++) {
            g[from[i]][--p[from[i]]] = to[i];
            g[to[i]][--p[to[i]]] = from[i];
        }
        return g;
    }

    public int dfs(int[][] g, int v, int p) {
        int depth = 0;
        for (int x : g[v])
            if (x != p) {
                depth = Math.max(depth, dfs(g, x, v));
            }
        return depth + 1;
    }

    public static void main(String[] args) {
        new Thread(null, new Main(), "1", 1 << 29).start();
    }
    public void run() {
        long t0 = System.currentTimeMillis();
        int n = 2300000;
        int[] from = new int[n - 1], to = new int[n - 1];
        for (int i = 0; i < n - 1; i++) {
            from[i] = i;
            to[i] = i + 1;
        }
        int[][] g = packU(n, from, to);
        long[] dp = new long[n];
        long t1 = System.currentTimeMillis();
        System.out.format("time to read data and build graph = %dms\n", t1 - t0);

        int depth = dfs(g, 0, -1);
        long t2 = System.currentTimeMillis();
        System.out.format("depth = %d\n", depth);
        System.out.format("time in dfs = %dms\n", t2 - t1);
        System.out.format("total time = %dms\n", t2 - t0);
    }
}
        