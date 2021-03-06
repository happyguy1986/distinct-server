package com.github.distinct_server.bloomfilter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

public class PartitionBloomFilter {
	private static Logger logger = LoggerFactory.getLogger(PartitionBloomFilter.class);
	
	private String baseDir;
	private Map<String,BloomFilter> partitions = new HashMap(200);
	
	public PartitionBloomFilter(String baseDir) {
		super();
		this.baseDir = baseDir;
	}

	public BloomFilter getBloomFilter(String partition) {
		Assert.hasText(partition,"partition must be not empty");
		BloomFilter bf = partitions.get(partition);
		if(bf == null) {
			try {
				bf = loadBloomFilterByPartition(partition);
				logger.info("loadBloomFilterByPartition(), partition:"+partition +" partitionFile:" + partitionFile(partition)  + " BloomFilter:"+bf);
				partitions.put(partition,bf);
			}catch(Exception e) {
				throw new RuntimeException("error on load partition:"+partition,e);
			}
		}
		return bf;
	}

	private synchronized BloomFilter loadBloomFilterByPartition(String partition) throws FileNotFoundException, IOException, ClassNotFoundException {
		Assert.hasText(partition,"partition must be not empty");
		File file = partitionFile(partition);
		if(file.exists()) {
			ObjectInputStream ois = null;
			try {
				ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file),128 * 1024));
				return (BloomFilter)ois.readObject();
			}finally {
				IOUtils.closeQuietly(ois);
			}
		}else {
			return new BloomFilter(0.001,Integer.MAX_VALUE / 4);
		}
	}

	public void dump() throws FileNotFoundException, IOException {
		for(String partition : partitions.keySet()) {
			try {
				long start = System.currentTimeMillis();
				dump(partition);
				long cost = System.currentTimeMillis() - start;
				logger.info("dumped partition cost_seconds:"+(cost/1000.0)+" partition:"+partition);
			}catch(Exception e) {
				logger.error("dump error",e);
			}
		}
	}

	/**
	 * 清除没有任何修改的BloomFilter 出内存
	 */
	public void clearNoChangeBloomFilter() {
		Set<String> keySet = new HashMap(partitions).keySet();
		for(String partition : keySet) {
			BloomFilter bf = partitions.get(partition);
			if(bf.isChange()){
				continue;
			}
			partitions.remove(partition);
			logger.info("clearNoChangeBloomFilter partition:"+partition+" from memory,partitionFile:"+partitionFile(partition));
		}
	}
	
	private void dump(String partition) throws IOException,
			FileNotFoundException {
		BloomFilter bf = partitions.get(partition);
		if(bf.isChange()) {
			bf.cleanChange();
			
			File file = partitionFile(partition);
			file.getParentFile().mkdirs();
			
			logger.info("start PartitionBloomFilter dump(), file:"+file+" BloomFilter:"+bf);
			ObjectOutputStream oos = null;
			try {
				oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file),128 * 1024));
				oos.writeObject(bf);
			}finally {
				IOUtils.closeQuietly(oos);
			}
		}
	}

	private File partitionFile(String partition) {
		return new File(baseDir,partition+".bloomfilter");
	}

}
