public class Main {
    private static int calcNumberOfWorkNodes() {
        long nodes = message.NumberOfNodes();
        int workNodes = 1;
        for (;;)
        {
            int nextWorkNodes = workNodes * 2;
            if (nextWorkNodes > nodes) {
                return workNodes;
            }
            workNodes = nextWorkNodes;
        }
    }

    public static void main(String[] args) {
        int nodes = calcNumberOfWorkNodes();
        long N = rps.GetN();
        int participants = 1 << N;
        if (participants / 2 < nodes) {
            nodes  = participants / 2;
        }

        int myNodeId = message.MyNodeId();
        if (myNodeId >= nodes) {
            return;
        }

        int masterId = 0;

        int blockLength = participants / nodes;
        int myStart = blockLength * myNodeId;
        int myEnd = myStart + blockLength;
        blockLength /= 2;
        int[] players = new int[blockLength];
        int offset = 0;
        for (int i = myStart; i < myEnd; i += 2, ++offset) {
            players[offset] = ChooseWinner(i, i + 1);
        }
        Process(players, blockLength);
        message.PutInt(masterId, players[0]);
        message.Send(masterId);
        if (myNodeId != masterId) {
            return;
        }

        players = new int[nodes];
        for (int i = 0; i < nodes; ++i) {
            int source = message.Receive(-1);
            players[source] = message.GetInt(source);
        }
        Process(players, nodes);
        System.out.println(players[0]);
    }

    private static int ChooseWinner(int leftIndex, int rightIndex) {
        char left = rps.GetFavoriteMove(leftIndex);
        char right = rps.GetFavoriteMove(rightIndex);
        switch (left) {
            case 'R':
                switch(right) {
                    case 'P':
                        return rightIndex;
                    default: // R or S
                        return leftIndex;
                }
            case 'P':
                switch(right) {
                    case 'S':
                        return rightIndex;
                    default: // R or P
                        return leftIndex;
                }
            default: //'S'
                switch(right) {
                    case 'R':
                        return rightIndex;
                    default: // S or P
                        return leftIndex;
                }
        }
    }

    private static void Process(int[] players, int blockLength)
    {
        while (blockLength > 1) {
            int j = 0;
            for (int i = 0; i < blockLength; i += 2, ++j) {
                players[j] = ChooseWinner(players[i], players[i + 1]);
            }
            blockLength = j;
        }
    }
}
