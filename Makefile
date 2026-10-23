CC = gcc
CFLAGS = -Wall -Wextra -O2
JAVAC = javac

# Targets
all: oracle_node vn

# Compile Oracle Node
on: oracle_node.c
	$(CC) $(CFLAGS) oracle_node.c -o oracle_node

# Compile Virtual Node
vn: VN.java
	$(JAVAC) VN.java

# Clean compiled files
clean:
	rm -f oracle_node *.class

.PHONY: all clean