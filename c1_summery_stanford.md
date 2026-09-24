The controlling constraint is that the frozen Election constructor receives only a port list, not an explicit port-to-node map; under the project’s contract that list is ordered by node ID. I’ll implement the state machine around that contract: asynchronous ELECTION sends, a timed OK window, self-coordination when no higher node responds, immediate asynchronous OK replies, and coordinator handling that cancels stale election rounds.

## HOW IT WORKS
Sends ELECTION messages asynchronously to higher-priority nodes.
Uses a real timeout before declaring itself coordinator.
Replies to ELECTION with OK immediately.
Runs local election work on a separate daemon thread.
Tracks incoming OK responses.
Broadcasts COORDINATOR when elected.
Cancels stale election rounds when a coordinator is received.
Copies the peer list defensively.
Updated ChatHandler.java to dispatch ELECTION, OK, and COORDINATOR messages correctly.