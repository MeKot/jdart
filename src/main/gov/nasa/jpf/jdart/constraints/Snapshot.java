package gov.nasa.jpf.jdart.constraints;

import com.romix.scala.collection.concurrent.TrieMap;
import ctrie.CTrieMap;
import ctrie.CoordinatorCTrie;
import seedbag.BatchedBlockingQueue;
import seedbag.CoordinatorSeedBag;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

public class Snapshot implements Runnable {
    public static final BatchedBlockingQueue<HashMap<String, Object>> seedBag = new CoordinatorSeedBag<>("localhost", 8080);
    public static final CTrieMap<String, Integer> cTrieMap = new CoordinatorCTrie<>("localhost", 8080);
    public static AtomicReference<TrieMap<String, Integer>> snapshot = new AtomicReference<>(cTrieMap.snapshot());


    @Override
    public void run() {
        Snapshot.snapshot.set(Snapshot.cTrieMap.snapshot());
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
