package main

import (
	"encoding/binary"
	"fmt"
	"math/rand"
	"os"
	"sync"
	"time"

	"github.com/dgraph-io/badger/v4"
	bbolt "go.etcd.io/bbolt"
)

const ITERATIONS = 100000

var keys []string
var stringValues []string
var intValues []int32
var opSequence []int

func initData() {
	keys = make([]string, ITERATIONS)
	stringValues = make([]string, ITERATIONS)
	intValues = make([]int32, ITERATIONS)
	opSequence = make([]int, ITERATIONS)

	for i := 0; i < ITERATIONS; i++ {
		keys[i] = fmt.Sprintf("key_long_prefix_to_test_hashing_%d", i)
		stringValues[i] = fmt.Sprintf("val_string_payload_with_some_length_%d_%d", i, rand.Int())
		intValues[i] = rand.Int31()
		opSequence[i] = rand.Intn(4)
	}
}

func runNextKV() {
	os.Remove("nextkv_go_sp.data")
	kv := NewNextKV("nextkv_go_sp.data", false) // SP mode
	defer kv.Close()

	start := time.Now()
	for i := 0; i < ITERATIONS; i++ {
		kv.PutInt(keys[i], intValues[i])
	}
	fmt.Printf("NextKV(CGo) ST PUT (Int):    %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	for i := 0; i < ITERATIONS; i++ {
		kv.GetInt(keys[i], 0)
	}
	fmt.Printf("NextKV(CGo) ST GET (Int):    %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	for i := 0; i < ITERATIONS; i++ {
		kv.PutString(keys[i], stringValues[i])
	}
	fmt.Printf("NextKV(CGo) ST PUT (String): %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	for i := 0; i < ITERATIONS; i++ {
		kv.GetString(keys[i])
	}
	fmt.Printf("NextKV(CGo) ST GET (String): %d ms\n", time.Since(start).Milliseconds())

	// Multi Threaded Mixed
	start = time.Now()
	var wg sync.WaitGroup
	threads := 4
	wg.Add(threads)
	for t := 0; t < threads; t++ {
		go func(tIdx int) {
			defer wg.Done()
			startIdx := tIdx * (ITERATIONS / threads)
			endIdx := (tIdx + 1) * (ITERATIONS / threads)
			for i := startIdx; i < endIdx; i++ {
				op := opSequence[i]
				if op == 0 {
					kv.PutString(keys[i], stringValues[i])
				} else if op == 1 {
					kv.GetString(keys[i])
				} else if op == 2 {
					kv.Remove(keys[i])
				} else {
					kv.Contains(keys[i])
				}
			}
		}(t)
	}
	wg.Wait()
	fmt.Printf("NextKV(CGo) MT MIXED (4 Th): %d ms\n", time.Since(start).Milliseconds())
}

func intToBytes(n int32) []byte {
	b := make([]byte, 4)
	binary.LittleEndian.PutUint32(b, uint32(n))
	return b
}

func runBadger() {
	os.RemoveAll("badger_data")
	opts := badger.DefaultOptions("badger_data").WithLoggingLevel(badger.WARNING)
	db, _ := badger.Open(opts)
	defer db.Close()

	start := time.Now()
	// Badger is too slow if we do 1 txn per put. We use WriteBatch
	wb := db.NewWriteBatch()
	for i := 0; i < ITERATIONS; i++ {
		wb.Set([]byte(keys[i]), intToBytes(intValues[i]))
	}
	wb.Flush()
	fmt.Printf("BadgerDB    ST PUT (Int):    %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	db.View(func(txn *badger.Txn) error {
		for i := 0; i < ITERATIONS; i++ {
			item, _ := txn.Get([]byte(keys[i]))
			item.Value(func(val []byte) error { return nil })
		}
		return nil
	})
	fmt.Printf("BadgerDB    ST GET (Int):    %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	wb = db.NewWriteBatch()
	for i := 0; i < ITERATIONS; i++ {
		wb.Set([]byte(keys[i]), []byte(stringValues[i]))
	}
	wb.Flush()
	fmt.Printf("BadgerDB    ST PUT (String): %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	db.View(func(txn *badger.Txn) error {
		for i := 0; i < ITERATIONS; i++ {
			item, _ := txn.Get([]byte(keys[i]))
			item.Value(func(val []byte) error { return nil })
		}
		return nil
	})
	fmt.Printf("BadgerDB    ST GET (String): %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	var wg sync.WaitGroup
	threads := 4
	wg.Add(threads)
	for t := 0; t < threads; t++ {
		go func(tIdx int) {
			defer wg.Done()
			startIdx := tIdx * (ITERATIONS / threads)
			endIdx := (tIdx + 1) * (ITERATIONS / threads)
			for i := startIdx; i < endIdx; i++ {
				op := opSequence[i]
				kb := []byte(keys[i])
				if op == 0 {
					db.Update(func(txn *badger.Txn) error { return txn.Set(kb, []byte(stringValues[i])) })
				} else if op == 1 || op == 3 {
					db.View(func(txn *badger.Txn) error {
						item, err := txn.Get(kb)
						if err == nil {
							item.Value(func(v []byte) error { return nil })
						}
						return nil
					})
				} else if op == 2 {
					db.Update(func(txn *badger.Txn) error { return txn.Delete(kb) })
				}
			}
		}(t)
	}
	wg.Wait()
	fmt.Printf("BadgerDB    MT MIXED (4 Th): %d ms\n", time.Since(start).Milliseconds())
}

func runBbolt() {
	os.Remove("bbolt.db")
	db, _ := bbolt.Open("bbolt.db", 0600, &bbolt.Options{NoSync: true})
	defer db.Close()
	db.Update(func(tx *bbolt.Tx) error {
		tx.CreateBucket([]byte("bucket"))
		return nil
	})

	start := time.Now()
	db.Update(func(tx *bbolt.Tx) error {
		b := tx.Bucket([]byte("bucket"))
		for i := 0; i < ITERATIONS; i++ {
			b.Put([]byte(keys[i]), intToBytes(intValues[i]))
		}
		return nil
	})
	fmt.Printf("Bbolt       ST PUT (Int):    %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	db.View(func(tx *bbolt.Tx) error {
		b := tx.Bucket([]byte("bucket"))
		for i := 0; i < ITERATIONS; i++ {
			b.Get([]byte(keys[i]))
		}
		return nil
	})
	fmt.Printf("Bbolt       ST GET (Int):    %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	db.Update(func(tx *bbolt.Tx) error {
		b := tx.Bucket([]byte("bucket"))
		for i := 0; i < ITERATIONS; i++ {
			b.Put([]byte(keys[i]), []byte(stringValues[i]))
		}
		return nil
	})
	fmt.Printf("Bbolt       ST PUT (String): %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	db.View(func(tx *bbolt.Tx) error {
		b := tx.Bucket([]byte("bucket"))
		for i := 0; i < ITERATIONS; i++ {
			b.Get([]byte(keys[i]))
		}
		return nil
	})
	fmt.Printf("Bbolt       ST GET (String): %d ms\n", time.Since(start).Milliseconds())

	start = time.Now()
	var wg sync.WaitGroup
	threads := 4
	wg.Add(threads)
	for t := 0; t < threads; t++ {
		go func(tIdx int) {
			defer wg.Done()
			startIdx := tIdx * (ITERATIONS / threads)
			endIdx := (tIdx + 1) * (ITERATIONS / threads)
			for i := startIdx; i < endIdx; i++ {
				op := opSequence[i]
				kb := []byte(keys[i])
				if op == 0 {
					db.Update(func(tx *bbolt.Tx) error { return tx.Bucket([]byte("bucket")).Put(kb, []byte(stringValues[i])) })
				} else if op == 1 || op == 3 {
					db.View(func(tx *bbolt.Tx) error { tx.Bucket([]byte("bucket")).Get(kb); return nil })
				} else if op == 2 {
					db.Update(func(tx *bbolt.Tx) error { return tx.Bucket([]byte("bucket")).Delete(kb) })
				}
			}
		}(t)
	}
	wg.Wait()
	fmt.Printf("Bbolt       MT MIXED (4 Th): %d ms\n", time.Since(start).Milliseconds())
}

func main() {
	fmt.Println("==================================================")
	fmt.Println("Starting Golang 3-Engine KV Benchmark on ARM64 MacOS...")
	fmt.Printf("Iterations: %d\n", ITERATIONS)
	fmt.Println("==================================================")
	initData()

	runNextKV()
	fmt.Println("--------------------------------------------------")
	runBadger()
	fmt.Println("--------------------------------------------------")
	runBbolt()
	fmt.Println("==================================================")
}