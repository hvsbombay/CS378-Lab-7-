#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/socket.h>
#include <unistd.h>
#include <arpa/inet.h>

#define ON_PORT 5000
#define MAX_NODES 26

typedef struct {
    char id;
    int sock_fd;
    uint32_t ip;
    uint16_t udp_port;
    int connected;
}VNNode;

VNNode vnnodes[MAX_NODES];
int num_connected_tcp = 0; 

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

        if(row_count >= MAX_NODES - 1) {
            fprintf(stderr, "Sanity Check Failed: Too many rows in config file\n");
            fclose(fp);
            return 0;
        }

        int col_count = 0;
        char *token = strtok(p, " \t\r\n");
        while (token != NULL) {
            if (col_count >= MAX_NODES -1 - row_count) {
                fprintf(stderr, "Sanity Check Failed: Too many columns in row %d\n", 'A' +row_count);
                fclose(fp);
                return 0;
            }
            
            errno = 0;
            char *end;
            long val = strtol(token, &end, 10);

            if (errno != 0 || end == token || *end != '\0' || val < -1 || val > 2147483647L) {
                fprintf(stderr, "Sanity Check Failed: Invalid integer value '%s'\n", token);
                fclose(fp);
                return 0;
            }

            temp_rows[row_count][col_count] = (int)val;
            col_count++;

            token = strtok(NULL, " \t\r\n");
        }

            row_lengths[row_count] = col_count;
            row_count++;
    }
    fclose(fp);

    int n = row_count + 1;
    if (n < 2 || n > MAX_NODES) {
        fprintf(stderr, "Sanity Check Failed: Invalid node count (%d)\n", n);
        return 0;
    }

    for (int i = 0; i < row_count; i++) {
        if (row_lengths[i] != (n - 1 - i)) {
            fprintf(stderr, "Sanity Check Failed: Row %d has %d cols, expected %d\n", 'A' + i, row_lengths[i], n - 1 - i);
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
        return EXIT_FAILURE;
    }

    if (!parse_config(argv[1])) {
        return EXIT_FAILURE;
    }

    printf("Oracle Node loaded config for %d nodes.\n", num_config_nodes);

    for (int i =0; i < MAX_NODES; i++) {
        vnnodes[i].sock_fd = -1;
        vnnodes[i].connected = 0;
    }

    printf("Adjacency Matrix:\n");
    for (int i = 0; i < num_config_nodes; i++) {
        for (int j = 0; j < num_config_nodes; j++) {
            printf("%d ", adj_matrix[i][j]);
        }
        printf("\n");
    }

    return EXIT_SUCCESS;
}
