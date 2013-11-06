/*
 *  GateApp.java
  */

package com.bobboau.GateApp;


import gate.Document;
import gate.Gate;
import gate.corpora.CorpusImpl;
import gate.creole.ResourceInstantiationException;
import gate.event.CreoleEvent;
import gate.util.GateException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.*;


/**
 * @author Bobboau
 *
 */
public class GateApp implements GateAppType
{	
	/**
	 * things that are listening to our events
	 */
	private List<GateAppType.GateAppListener> listeners = new ArrayList<GateAppType.GateAppListener>();
	
	/**
	 * key/value pair configuration object
	 */
	private Config config;
	
	/**
	 * set of documents we are working on
	 */
	private CorpusImpl corpus = null;
	
	/**
	 * constructor, starts up the Gate application
	 */
	public GateApp()
	{
		construct();
	}
	
	/**
	 * constructor, starts up the Gate application
	 * @param listener	 */
	public GateApp(GateAppListener listener)
	{
		addListener(listener);
		construct();
	}
	
	/**
	 * construction common code
	 * @throws GateException 
	 */
	protected void construct()
	{
		
		try
		{
			Gate.init();
			this.corpus = new CorpusImpl(){
				public void resourceLoaded(CreoleEvent e){
					super.resourceLoaded(e);
					GateApp.this.documentLoaded();
				}
			};
			this.config = Config.load("GateApp.conf"); //load the application configuration settings
			
			for(GateAppListener gate_listener : this.listeners)
			{
				gate_listener.onGateInit();
			}
			
			//load up what ever corpus we had last time, default to nothing
			setCorpus(new URL(this.config.get("loaded_files", "")));
		}
		catch (IOException e)
		{
			//if it fails just log it, don't worry about it too much
			e.printStackTrace();
		}
		catch (GateException e1)
		{
			for(GateAppListener gate_listener : this.listeners)
			{
				gate_listener.onGateFailed();
			}

		}
	}
	
	/**
	 * @param gate_listener
	 */
	@Override
	public void addListener(GateAppListener gate_listener)
	{
		this.listeners.add(gate_listener);
	}
	
	/**
	 * loads up a list of files
	 * @param document_directory
	 */
	@Override
	public void setCorpus(URL document_directory)
	{
		FileFilter filter = new FileFilter(){
			@Override
			public boolean accept(File file)
			{
				return file.getName().endsWith(".pdf");
			}
		};
		
		int file_count = new File(document_directory.getFile()).listFiles(filter).length;
		
		for(GateAppListener gate_listener : this.listeners)
		{
			gate_listener.onCorpusLoadStart(file_count);
		}
		
		try
		{
			this.corpus.populate(
				document_directory, 
				filter,
				"UTF-8",
				false
			);
		}
		catch (ResourceInstantiationException | IOException e)
		{
			for(GateAppListener gate_listener : this.listeners)
			{
				gate_listener.onCorpusLoadFailed();
			}
		}
		
		this.config.set("loaded_files", document_directory.toString());
		for(GateAppListener gate_listener : this.listeners)
		{
			gate_listener.onCorpusLoadComplete(getCorpus());
		}
	}
	
	/**
	 * called whenever a corpus loads a document
	 */
	public void documentLoaded(){
		for(GateAppListener gate_listener : this.listeners)
		{
			gate_listener.onCorpusDocumentLoaded();
		}
	}
	
	/**
	 * @return list of files loaded into the corpus
	 */
	private List<URL> getCorpus()
	{
		ArrayList<URL> file_list = new ArrayList<URL>();
		for(Document document : this.corpus){
			file_list.add(document.getSourceUrl());
		}
		return file_list;
	}
	
	/**
	 * @param idx
	 */
	@Override
	public void getDocumentContent(int idx, ResultRetriever results){
		results.string(this.corpus.get(idx).getContent().toString());
	}
	
	/**
	 * @param idx
	 */
	@Override
	public void getDocumentSubject(int idx, ResultRetriever results){
		results.string("Cool NLP stuff about "+this.corpus.get(idx).getName()+" goes here.");
	}

} // class GateApp
