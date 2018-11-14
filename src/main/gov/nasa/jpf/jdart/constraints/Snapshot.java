package gov.nasa.jpf.jdart.constraints;

import com.romix.scala.collection.concurrent.TrieMap;
import ctrie.CTrieMap;
import ctrie.CoordinatorCTrie;
import seedbag.BatchedBlockingQueue;
import seedbag.CoordinatorSeedBag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class Snapshot implements Runnable {
    static final BatchedBlockingQueue<HashMap<String, Object>> seedBag = new CoordinatorSeedBag<>("localhost", 8080);
    static final CTrieMap<String, Integer> cTrieMap = new CoordinatorCTrie<>("localhost", 8080);
    static AtomicReference<TrieMap<String, Integer>> snapshot = new AtomicReference<>(cTrieMap.snapshot());
    static final Set<String> alreadyPutIn = new HashSet<>();


    @Override
    public void run() {
        while (true) {
            Snapshot.snapshot.set(Snapshot.cTrieMap.snapshot());
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
