#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define ON_PORT 5000
#define MAX_NODES 26

int adj_matrix[MAX_NODES][MAX_NODES];
int num_config_nodes = 0;

int parse_config(const char *filename) {
    FILE *fp = fopen(filename, "r");
    if (!fp) {
        perror("Failed to open config file");
        return 0;
    }

    int temp_rows[MAX_NODES][MAX_NODES];
    int row_lengths[MAX_NODES];
    int row_count = 0;
    char line[1024];

    while (fgets(line, sizeof(line), fp)) {
        char *p = line;
        while (*p == ' ' || *p == '\t') p++;

        if (*p == '#' || *p == '\n' || *p == '\r' || *p == '\0') continue;

        int col_count = 0;
        char *token = strtok(p, " \t\r\n");
        while (token != NULL) {
            if (col_count < MAX_NODES) {
                temp_rows[row_count][col_count++] = atoi(token);
            }
            token = strtok(NULL, " \t\r\n");
        }

        if (col_count > 0) {
            row_lengths[row_count] = col_count;
            row_count++;
            if (row_count >= MAX_NODES) break;
        }
    }
    fclose(fp);

    int n = row_count + 1;
    if (n <= 1 || n > MAX_NODES) {
        fprintf(stderr, "Sanity Check Failed: Invalid node count (%d)\n", n);
        return 0;
    }

    for (int i = 0; i < row_count; i++) {
        if (row_lengths[i] != (n - 1 - i)) {
            fprintf(stderr, "Sanity Check Failed: Row %d has %d cols, expected %d\n",
                    i, row_lengths[i], n - 1 - i);
            return 0;
        }
    }

   num_config_nodes = n;
    for (int i = 0; i < n; i++) {
        adj_matrix[i][i] = 0;
        for (int j = i + 1; j < n; j++) {
            int cost = temp_rows[i][j - (i + 1)];
            adj_matrix[i][j] = cost;
            adj_matrix[j][i] = cost; 
        }
    }
    return 1;
}

//#to check
int main (int argc, char *argv[]){
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <config_file> [ON_Bind_IP]\n", argv[0]);
        exit(EXIT_FAILURE);
    if (!parse_config(argv[1])) {
        return EXIT_FAILURE;
    }

    printf("Oracle Node loaded config for %d nodes.\n", num_config_nodes);
    return EXIT_SUCCESS;
    }
}
