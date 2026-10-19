#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <sys/socket.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/select.h>

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
int send_all(int sock_fd, const uint8_t *data ,size_t length){
    size_t total=0;
    while (total<length)
    {
        ssize_t sent= send(sock_fd, data+total,length-total,0);
        if(sent<=0){
            perror("send failed");
            return 0;
        }
        total+=(size_t)sent;
    }
    return 1;
}


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
    int listen_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (listen_sock < 0) {
        perror("Socket creation failed");
        return EXIT_FAILURE;
    }
    // Bind failed: Address already in use - to fix it  or use fuser -k 5000/tcp on terminal
    int opt = 1;
    setsockopt(listen_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(ON_PORT);
    server_addr.sin_addr.s_addr = htons(INADDR_ANY);

    if(bind(listen_sock, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        perror("Bind failed");
        return EXIT_FAILURE;
    }
    if(listen(listen_sock, MAX_NODES) < 0) {
        perror("Listen failed");
        return EXIT_FAILURE;
    }
    printf("Oracle Node listening on port %d\n", ON_PORT);

    //single threaded loop
    while(1) {
        fd_set read_fds;
        FD_ZERO(&read_fds);
        FD_SET(listen_sock, &read_fds);
        int max_fd = listen_sock;
    

    for (int i = 0; i < num_config_nodes; i++) {
        if (vnnodes[i].sock_fd >= 0) {
            FD_SET(vnnodes[i].sock_fd, &read_fds);
            if (vnnodes[i].sock_fd > max_fd) {
                max_fd = vnnodes[i].sock_fd;
            }
        }
    }
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;

    int activity = select(max_fd + 1, &read_fds, NULL, NULL, &timeout);
    if (activity < 0) {
        perror("select() fail");
        break;
    }

    //listen socket new vn connect
    if (FD_ISSET(listen_sock, &read_fds)) {
        struct sockaddr_in client_addr;
        socklen_t client_len = sizeof(client_addr);
        int new_fd = accept(listen_sock, (struct sockaddr*)&client_addr, &client_len);
        if (new_fd >= 0 && num_connected_tcp < MAX_NODES) {
            int node_index = num_connected_tcp++;
            vnnodes[node_index].id = 'A' + node_index;
            vnnodes[node_index].sock_fd = new_fd;
            printf("accept() new TCP connection -> Assigned VN: %c\n", vnnodes[node_index].id);
        }
        }

    for (int i = 0; i < num_config_nodes; i++) {
        int sd = vnnodes[i].sock_fd; 
        if(sd >= 0 && FD_ISSET(sd, &read_fds)) {
            uint8_t msg[7];

            int valread = recv(sd, msg, sizeof(msg), MSG_WAITALL);

            if(valread <= 0) {
                printf("VN %c disconnected\n", vnnodes[i].id);
                close(sd);
                vnnodes[i].sock_fd = -1;
            } 
            else if (valread == sizeof(msg) && msg[0] == 1) {
                memcpy(&vnnodes[i].ip, &msg[1], sizeof(uint32_t));
                
                uint16_t net_port;
                memcpy(&net_port, &msg[5], sizeof(uint16_t));
                vnnodes[i].udp_port = ntohs(net_port);

                vnnodes[i].connected = 1;
                //binarry to string
                char ip_str[INET_ADDRSTRLEN];
                inet_ntop(AF_INET, &vnnodes[i].ip, ip_str, INET_ADDRSTRLEN);
                printf("VN %c connected with IP: %s and UDP port: %d\n", vnnodes[i].id, ip_str, vnnodes[i].udp_port);
            }
        }
    }
        usleep(100000); // Sleep for 100ms to avoid busy waiting    
    }
    return EXIT_SUCCESS;
}
