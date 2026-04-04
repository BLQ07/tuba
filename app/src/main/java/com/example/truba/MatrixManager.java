package com.example.truba;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MatrixManager {
    private static final String PREFS_NAME = "matrices";
    private SharedPreferences prefs;
    private Gson gson;

    public MatrixManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveMatrix(String name, double[][] matrix) {
        String json = gson.toJson(matrix);
        prefs.edit().putString(name, json).apply();
        Log.d("MatrixManager", "Saved matrix " + name + ": " + matrix.length + "x" + matrix[0].length);
    }

    public double[][] getMatrix(String name) {
        String json = prefs.getString(name, null);
        if (json == null) {
            Log.d("MatrixManager", "Matrix " + name + " not found");
            return null;
        }
        Type type = new TypeToken<double[][]>(){}.getType();
        double[][] matrix = gson.fromJson(json, type);
        Log.d("MatrixManager", "Loaded matrix " + name + ": " + matrix.length + "x" + matrix[0].length);
        return matrix;
    }

    public void removeMatrix(String name) {
        prefs.edit().remove(name).apply();
        Log.d("MatrixManager", "Removed matrix " + name);
    }

    public Map<String, double[][]> getAllMatrices() {
        Map<String, ?> all = prefs.getAll();
        Map<String, double[][]> matrices = new HashMap<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof String) {
                double[][] mat = getMatrix(entry.getKey());
                if (mat != null) matrices.put(entry.getKey(), mat);
            }
        }
        return matrices;
    }

    public void clearAll() {
        prefs.edit().clear().apply();
        Log.d("MatrixManager", "Cleared all matrices");
    }

    // ==================== МАТРИЧНЫЕ ОПЕРАЦИИ ====================

    public static double[][] add(double[][] A, double[][] B) {
        checkSameSize(A, B);
        int rows = A.length, cols = A[0].length;
        double[][] C = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                C[i][j] = A[i][j] + B[i][j];
        return C;
    }

    public static double[][] subtract(double[][] A, double[][] B) {
        checkSameSize(A, B);
        int rows = A.length, cols = A[0].length;
        double[][] C = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                C[i][j] = A[i][j] - B[i][j];
        return C;
    }

    public static double[][] multiply(double[][] A, double[][] B) {
        if (A[0].length != B.length)
            throw new IllegalArgumentException("Несогласованные размеры для умножения: " + A[0].length + " != " + B.length);
        int m = A.length, n = A[0].length, p = B[0].length;
        double[][] C = new double[m][p];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < p; j++)
                for (int k = 0; k < n; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }

    public static double[][] elementWiseMultiply(double[][] A, double[][] B) {
        checkSameSize(A, B);
        int rows = A.length, cols = A[0].length;
        double[][] C = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                C[i][j] = A[i][j] * B[i][j];
        return C;
    }

    public static double[][] transpose(double[][] A) {
        int rows = A.length, cols = A[0].length;
        double[][] T = new double[cols][rows];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                T[j][i] = A[i][j];
        return T;
    }

    public static double determinant(double[][] A) {
        int n = A.length;
        if (n != A[0].length) throw new IllegalArgumentException("Не квадратная матрица");
        if (n == 1) return A[0][0];
        if (n == 2) return A[0][0] * A[1][1] - A[0][1] * A[1][0];
        double det = 0;
        for (int i = 0; i < n; i++) {
            double[][] sub = new double[n - 1][n - 1];
            for (int j = 1; j < n; j++) {
                int col = 0;
                for (int k = 0; k < n; k++) {
                    if (k == i) continue;
                    sub[j - 1][col++] = A[j][k];
                }
            }
            det += Math.pow(-1, i) * A[0][i] * determinant(sub);
        }
        return det;
    }

    public static double trace(double[][] A) {
        int n = Math.min(A.length, A[0].length);
        double sum = 0;
        for (int i = 0; i < n; i++) sum += A[i][i];
        return sum;
    }

    public static double[][] inverse(double[][] A) {
        int n = A.length;
        if (n != A[0].length) throw new IllegalArgumentException("Матрица не квадратная");
        double[][] augmented = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, augmented[i], 0, n);
            augmented[i][n + i] = 1;
        }
        for (int i = 0; i < n; i++) {
            int maxRow = i;
            for (int k = i + 1; k < n; k++)
                if (Math.abs(augmented[k][i]) > Math.abs(augmented[maxRow][i]))
                    maxRow = k;
            double[] temp = augmented[i];
            augmented[i] = augmented[maxRow];
            augmented[maxRow] = temp;
            double pivot = augmented[i][i];
            if (Math.abs(pivot) < 1e-12)
                throw new IllegalArgumentException("Матрица вырождена");
            for (int j = i; j < 2 * n; j++)
                augmented[i][j] /= pivot;
            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = augmented[k][i];
                    for (int j = i; j < 2 * n; j++)
                        augmented[k][j] -= factor * augmented[i][j];
                }
            }
        }
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++)
            System.arraycopy(augmented[i], n, inv[i], 0, n);
        return inv;
    }

    public static int rank(double[][] A) {
        double[][] B = copy(A);
        int rows = B.length, cols = B[0].length;
        int rank = 0;
        for (int i = 0; i < cols; i++) {
            int maxRow = rank;
            for (int j = rank; j < rows; j++) {
                if (Math.abs(B[j][i]) > Math.abs(B[maxRow][i]))
                    maxRow = j;
            }
            if (Math.abs(B[maxRow][i]) < 1e-12) continue;
            double[] tmp = B[rank];
            B[rank] = B[maxRow];
            B[maxRow] = tmp;
            double pivot = B[rank][i];
            for (int j = i; j < cols; j++)
                B[rank][j] /= pivot;
            for (int j = 0; j < rows; j++) {
                if (j != rank && Math.abs(B[j][i]) > 1e-12) {
                    double factor = B[j][i];
                    for (int k = i; k < cols; k++)
                        B[j][k] -= factor * B[rank][k];
                }
            }
            rank++;
            if (rank == rows) break;
        }
        return rank;
    }

    public static double conditionNumber(double[][] A) {
        double norm1 = 0, normInf = 0;
        int n = A.length;
        for (int i = 0; i < n; i++) {
            double rowSum = 0, colSum = 0;
            for (int j = 0; j < n; j++) {
                rowSum += Math.abs(A[i][j]);
                colSum += Math.abs(A[j][i]);
            }
            if (rowSum > normInf) normInf = rowSum;
            if (colSum > norm1) norm1 = colSum;
        }
        double[][] inv = inverse(A);
        double invNorm1 = 0, invNormInf = 0;
        for (int i = 0; i < n; i++) {
            double rowSum = 0, colSum = 0;
            for (int j = 0; j < n; j++) {
                rowSum += Math.abs(inv[i][j]);
                colSum += Math.abs(inv[j][i]);
            }
            if (rowSum > invNormInf) invNormInf = rowSum;
            if (colSum > invNorm1) invNorm1 = colSum;
        }
        return Math.max(norm1 * invNorm1, normInf * invNormInf);
    }

    public static double norm(double[][] v) {
        if (v.length == 1) {
            double sum = 0;
            for (double val : v[0]) sum += val * val;
            return Math.sqrt(sum);
        } else if (v[0].length == 1) {
            double sum = 0;
            for (int i = 0; i < v.length; i++) sum += v[i][0] * v[i][0];
            return Math.sqrt(sum);
        } else throw new IllegalArgumentException("Не вектор");
    }

    public static double dot(double[][] u, double[][] v) {
        if ((u.length == 1 && v.length == 1 && u[0].length == v[0].length) ||
                (u[0].length == 1 && v[0].length == 1 && u.length == v.length)) {
            double sum = 0;
            if (u.length == 1) {
                for (int i = 0; i < u[0].length; i++) sum += u[0][i] * v[0][i];
            } else {
                for (int i = 0; i < u.length; i++) sum += u[i][0] * v[i][0];
            }
            return sum;
        } else throw new IllegalArgumentException("Несовместимые векторы");
    }

    public static double[][] cross(double[][] u, double[][] v) {
        if ((u.length == 1 && u[0].length == 3) || (u[0].length == 1 && u.length == 3)) {
            double ux, uy, uz, vx, vy, vz;
            if (u.length == 1) {
                ux = u[0][0]; uy = u[0][1]; uz = u[0][2];
                vx = v[0][0]; vy = v[0][1]; vz = v[0][2];
            } else {
                ux = u[0][0]; uy = u[1][0]; uz = u[2][0];
                vx = v[0][0]; vy = v[1][0]; vz = v[2][0];
            }
            double[][] res = new double[1][3];
            res[0][0] = uy * vz - uz * vy;
            res[0][1] = uz * vx - ux * vz;
            res[0][2] = ux * vy - uy * vx;
            return res;
        } else throw new IllegalArgumentException("Векторное произведение только для 3D");
    }

    public static double[][] solveLinear(double[][] A, double[][] b) {
        int n = A.length;
        if (n != A[0].length) throw new IllegalArgumentException("A не квадратная");
        if (b.length != n || b[0].length != 1)
            throw new IllegalArgumentException("b должен быть вектором-столбцом");
        double[][] augmented = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, augmented[i], 0, n);
            augmented[i][n] = b[i][0];
        }
        for (int i = 0; i < n; i++) {
            int maxRow = i;
            for (int k = i + 1; k < n; k++)
                if (Math.abs(augmented[k][i]) > Math.abs(augmented[maxRow][i]))
                    maxRow = k;
            double[] temp = augmented[i];
            augmented[i] = augmented[maxRow];
            augmented[maxRow] = temp;
            double pivot = augmented[i][i];
            if (Math.abs(pivot) < 1e-12)
                throw new IllegalArgumentException("Матрица вырождена");
            for (int j = i; j <= n; j++)
                augmented[i][j] /= pivot;
            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = augmented[k][i];
                    for (int j = i; j <= n; j++)
                        augmented[k][j] -= factor * augmented[i][j];
                }
            }
        }
        double[][] x = new double[n][1];
        for (int i = 0; i < n; i++) x[i][0] = augmented[i][n];
        return x;
    }

    public static double[][] eye(int n) {
        double[][] I = new double[n][n];
        for (int i = 0; i < n; i++) I[i][i] = 1;
        return I;
    }

    public static double[][] zeros(int rows, int cols) {
        return new double[rows][cols];
    }

    public static double[][] ones(int rows, int cols) {
        double[][] O = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                O[i][j] = 1;
        return O;
    }

    private static void checkSameSize(double[][] A, double[][] B) {
        if (A.length != B.length || A[0].length != B[0].length)
            throw new IllegalArgumentException("Размеры матриц не совпадают: " + A.length + "x" + A[0].length + " != " + B.length + "x" + B[0].length);
    }

    private static double[][] copy(double[][] A) {
        int rows = A.length, cols = A[0].length;
        double[][] B = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            System.arraycopy(A[i], 0, B[i], 0, cols);
        return B;
    }
}