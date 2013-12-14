package com.bobboau.GateApp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Corpus;
import gate.Document;
import gate.util.InvalidOffsetException;

/**
 * @author Bobboau
 * finds blocks of terms and generates a score for them, basically this is the list of all possible extractions
 */
public class TermBlocks {
	
	/**
	 * represents a contiguous sequence of terms
	 * @author Bobboau
	 *
	 */
	private class Block{
		Block(Annotation[] values, double score){
			this.values = new Annotation[TermBlocks.this.block_size];
			System.arraycopy(values, 0, this.values, 0, TermBlocks.this.block_size);
			this.score = score;
		}
		Annotation[] values;
		long getDocumentStart(){
			for(int i = 0; i < TermBlocks.this.block_size; i++){
				if(this.values[i] != null){
					return this.values[i].getStartNode().getOffset().longValue();
				}
			}
			return 0L;
			
		}
		long getDocumentStop(){
			for(int i = TermBlocks.this.block_size-1; i>-1; i--){
				if(this.values[i] != null){
					return this.values[i].getEndNode().getOffset().longValue();
				}
			}
			return 0L;
		}
		double score;
	}
	
	/**
	 * class that represents a section of text in a document
	 * @author Bobboau
	 */
	private class Blob {
		public long start;
		public long end;
	}
	
	/**
	 * the tfidf calculator
	 */
	private Tfidf tfidf = null;
	
	/**
	 * the corpus
	 */
	private Corpus corpus = null;
	
	/**
	 * the blocks
	 */
	private ArrayList<ArrayList<Block>> blocks = null;
	
	/**
	 * block size
	 */
	private int block_size = 0;
	
	/**
	 * change block size
	 * @param block_size
	 */
	public void setBlockSize(int block_size){
		this.block_size = block_size;
		calculate();
	}
	
	/**
	 * get block size
	 * @return size of the block as it is currently set
	 */
	public int getBlockSize(){
		return this.block_size;
	}

	/**
	 * sets the corpus
	 * @param corpus
	 */
	public void setCorpus(Corpus corpus){
		this.tfidf.setCorpus(corpus);
		this.corpus = corpus;
		calculate();
	}
	
	/**
	 * have to do this because for some reason inDocumentOrder is not available like the documentation describes
	 * @param annotation_set
	 * @return
	 */
	private ArrayList<Annotation> getOrderedAnnotations(AnnotationSet annotation_set){
		ArrayList<Annotation> annotations = new ArrayList<Annotation>();
		for(Annotation annotation : annotation_set){
			annotations.add(annotation);
		}
		Collections.sort(annotations, new Comparator<Annotation>(){
			@Override
			public int compare(Annotation a, Annotation b) {
				return (int) (a.getStartNode().getOffset() - b.getStartNode().getOffset());
			}
		});
		return annotations;
	}
	
	private void calculate(){
		if(this.block_size < 1 || this.corpus == null){
			return;
		}

		this.blocks = new ArrayList<ArrayList<Block>>();
		Annotation[] working_set = new Annotation[this.block_size];
		for(int i = 0; i<this.corpus.size(); i++)
		{
			clearWorkingSet(working_set);
			ArrayList<Block> doc_blocks = new ArrayList<Block>(); 
			Document document = this.corpus.get(i);

			Set<String> types = new HashSet<String>();
			types.add("Term");
			types.add("MessageHeader");
			
			
			for(Annotation annotation : getOrderedAnnotations(document.getAnnotations().get(types))){
				if(annotation.getType().equals("MessageHeader"))
				{
					if(!workingSetIsReady(working_set)){
						doc_blocks.add(new Block(working_set, workingSetScore(working_set, i)));
					}
					clearWorkingSet(working_set);
				}
				else if(annotation.getType().equals("Term"))
				{
					pushWorkingSet(working_set, annotation);
					if(workingSetIsReady(working_set)){
						doc_blocks.add(new Block(working_set, workingSetScore(working_set, i)));
					}
				}
			}
			this.blocks.add(doc_blocks);
		}
	}
	
	/**
	 * get the value of the working set
	 */
	private double workingSetScore(Annotation[]ws, int doc_idx){
		double value = 0.0;
		for(int i = 0; i<this.block_size; i++){
			if(ws[i] != null){
				value += this.tfidf.getScore(ws[i].getFeatures().get("string").toString(), doc_idx);
			}
		}
		return value;
	}
	
	/**
	 * tells if the working set is filled with enough stuff to count yet
	 */
	private boolean workingSetIsReady(Annotation[]ws){
		return ws[0] != null;
	}
	
	/**
	 * removes all state info
	 * @param ws
	 */
	private void clearWorkingSet(Annotation[]ws){
		for(int i = 0; i<this.block_size; i++){
			ws[i] = null;
		}
	}
	
	/**
	 * adds a new value to the working set
	 * @param ws
	 */
	private void pushWorkingSet(Annotation[]ws, Annotation value){
		for(int i = 1; i<this.block_size; i++){
			ws[i-1] = ws[i];
		}
		ws[this.block_size - 1] = value;
	}

	/**
	 * returns a list of strings ordered from greatest to least score
	 * @param idx the document to get blocks for
	 * @param merge_threshold if two blocks are within this ordinal distance and overlap, merge them into the higher position
	 * @return a list of strings extracted from the document that should be reasonable summarizations
	 */
	public List<String> getBlocksAsStrings(int idx, int merge_threshold) {
		if(this.block_size < 1 || this.corpus == null){
			return new ArrayList<String>();
		}
		
		ArrayList<Block> doc_blocks = getSortedBlocks(idx);
		
		ArrayList<Blob> blobs = mergeLocalText(merge_threshold, doc_blocks);
		
		return blobsToStrings(idx, blobs);
	}

	/**
	 * given a bunch of blobs and a document return a bunch of strings from that document
	 * @param document_idx
	 * @param blobs
	 * @return
	 */
	private List<String> blobsToStrings(int document_idx, ArrayList<Blob> blobs)
	{
		ArrayList<String> ret = new ArrayList<String>();
		for(Blob blob : blobs){
			try
			{
				ret.add(
					this.corpus.get(document_idx).getContent().getContent(
						new Long(blob.start),
						new Long(blob.end)
					).toString().replaceAll("[\\r\\n\\s]+", "  ")
				);
			}
			catch (InvalidOffsetException e)
			{
				e.printStackTrace();
			}
		}
		return ret;
	}

	/**
	 * given a bunch of blocks make a bunch of blobs that don't have any overlapping text near by (defined by merge threshold)
	 * @param merge_threshold
	 * @param doc_blocks
	 * @return
	 */
	private ArrayList<Blob> mergeLocalText(int merge_threshold, ArrayList<Block> doc_blocks)
	{
		ArrayList<Blob> blobs = new ArrayList<Blob>();
		
		//merge near by overlaps
		outter : for(Block block : doc_blocks){
			for(int j = 0; j<merge_threshold; j++){
				if(blobs.size() - 1 - j > -1){
					Blob old_blob = blobs.get(blobs.size() - 1 - j);
					//if this block overlaps an existing blob within threshold distance merge this block into that blob
					if(
						block.getDocumentStart() > old_blob.start && block.getDocumentStart() < old_blob.end
						||
						block.getDocumentStop() > old_blob.start && block.getDocumentStop() < old_blob.end
					){
						old_blob.start = Math.min(old_blob.start, block.getDocumentStart());
						old_blob.end = Math.min(old_blob.end, block.getDocumentStop());
						continue outter;
					}
				}
			}
			
			//if no overlaps were found just add the block as a new blob
			Blob new_blob = new Blob();
			new_blob.start = block.getDocumentStart();
			new_blob.end = block.getDocumentStop();
			blobs.add(new_blob);
		}
		return blobs;
	}

	/**
	 * get a list of blocks ordered by score
	 * @param idx
	 * @return
	 */
	private ArrayList<Block> getSortedBlocks(int idx)
	{
		ArrayList<Block> doc_blocks = new ArrayList<Block>();
		
		for(Block block : this.blocks.get(idx)){
			doc_blocks.add(block);
		}
		
		Collections.sort(doc_blocks, new Comparator<Block>(){
			@Override
			public int compare(Block a, Block b) {
				double diff = a.score - b.score;
				if(diff == 0.0){
					return 0;
				}
				return diff < 0.0 ? 1 : -1;
			}
		});
		return doc_blocks;
	}
	
	/**
	 * change the tfidf implementation
	 * @param new_tfidf
	 */
	void setTfidf(Tfidf new_tfidf){
		this.tfidf = new_tfidf;
		if(this.corpus != null){
			setCorpus(this.corpus);
		}
	}
}
