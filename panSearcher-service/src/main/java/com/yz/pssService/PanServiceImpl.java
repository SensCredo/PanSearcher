package com.yz.pssService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yz.pssBeans.PanResult;
import com.yz.pssDao.PanDao;

public class PanServiceImpl implements PanService {
	
	private PanDao dao;
	private List<String> panReg=new ArrayList<String>();
	private List<Pattern> panPat=new ArrayList<Pattern>();
	private Pattern passwordPat;
	private Pattern partPanPat1;
	private Pattern partPanPat2;
	
	public PanServiceImpl() {
	}
	
	public void setDao(PanDao dao) {
		this.dao = dao;
	}
	

	@Override
	public List<PanResult> searchPan(String searchItem, int searchDeepth) throws Exception {
		// ���ȴ����ݿ���ң����޽�����������н�������
		List<PanResult> results=dao.selectResult(searchItem);
		int lastSearcchDeepth=0;
		if(results.size()!=0)
			//��ȡ���ݿ��и���������������,���������С�ڸô�������ȣ���������н�������
			 lastSearcchDeepth=dao.selectDeepthByItem(searchItem);
		if((results.size()==0)||(searchDeepth>lastSearcchDeepth)){
			  PatternInit();
			  Set<String> searchLink=searchLinkSelector(searchItem,searchDeepth);
			  //ҳ������ƥ��
			  String pageNumReg="class=\"red\">\\d+</span>";
			  Pattern pageNumPat=Pattern.compile(pageNumReg);
			  int pageDeepth=1;
			  for (String link : searchLink) {
				  URL url=new URL(link);
				  HttpURLConnection conn=(HttpURLConnection) url.openConnection();
				  BufferedReader context = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				  String line;
				  while((line=context.readLine())!=null){
					  	byte[] charArray = line.getBytes("GBK");
						line=new String(charArray,"utf-8");
						Matcher pageNumMat=pageNumPat.matcher(line);
						//System.out.println(line);
						if(pageNumMat.find()){
							//ҳ������10ʱȡ���ҳ��������Ϊ10
							if(pageNumMat.group().matches("\\d\\d+"))
								pageDeepth=10;
							else
								pageDeepth=Integer.parseInt(pageNumMat.group().substring(12, 13));
						}
						PanResult result=searchPanlink(line,link);
						if(result!=null){
							result.setSearchItem(searchItem);
							result.setSearchDeepth(searchDeepth);
							//���ý�������ݿ��в����ڣ���������ݳ־û������洢�����ݿ⣩
							String searchItemInDB=dao.selectItemByLink(result.getPanUrl());
							if((searchItemInDB==null)||(!searchItemInDB.equals(searchItem))){
								dao.addPanResult(result);
								results.add(result);
							}		
						}		
					}
				  if(pageDeepth>1){
					  for (int i = 2; i <= pageDeepth; i++) {
						  //����ֹһҳ����������ʣ��ҳ��
						  link=link+"?pn="+i;
						  URL deepUrl=new URL(link);
						  HttpURLConnection deepConn=(HttpURLConnection) deepUrl.openConnection();
						  BufferedReader deepContext = new BufferedReader(new InputStreamReader(deepConn.getInputStream()));
						  while((line=deepContext.readLine())!=null){
							  byte[] charArray = line.getBytes("GBK");
							  line=new String(charArray,"utf-8");
							  PanResult result=searchPanlink(line,link);
							  if(result!=null){
									result.setSearchItem(searchItem);
									result.setSearchDeepth(searchDeepth);
									String searchItemInDB=dao.selectItemByLink(result.getPanUrl());
									if((searchItemInDB==null)||(!searchItemInDB.equals(searchItem))){
										dao.addPanResult(result);
										results.add(result);
									}
								}	
						  }
					}
				  }
			}
			 if(lastSearcchDeepth!=0){
				//���ݿ���searchDeepth���¼��ʷ����������
					dao.updateDeepthByItem(searchDeepth,searchItem);
			 }			
		}
		return results;
	}
	private void PatternInit() {
		 //��׼��ʽ����
		  panReg.add("pan.baidu.com/s/\\w{8}\\W");
		  panReg.add("pan.baidu.com/s/[\\w-]{23}");
		  panReg.add("share.weiyun.com/\\w{7}");
		  //���ָ�ʽ����
		  String partPanReg1="/s/\\w{8}\\W";
		  String partPanReg2="/s/[\\w-]{23}";
		  //��������ƥ��
		  String passwordReg="����[:��]\\s?\\w{4}";
		  for (String reg : panReg) {
				panPat.add(Pattern.compile(reg));
			}
		  partPanPat1=Pattern.compile(partPanReg1);
		  partPanPat2=Pattern.compile(partPanReg2);
		  passwordPat=Pattern.compile(passwordReg);		  
	}
	
	private Set<String> searchLinkSelector(String searchItem,int searchDeepth) throws Exception{
		Set<String> searchLink=new HashSet<String>();
		searchItem=URLEncoder.encode(searchItem,"utf-8");
		String line;
		for(int i=1;i<=searchDeepth;i++){
			//����ȶ������õ�����ҳ���ν��в���
			URL url=new URL("http://tieba.baidu.com/f/search/res?ie=utf-8&qw="+searchItem+"&rn=10&un=&only_thread=0&sm=1&sd=&ed=&pn="+i);
			HttpURLConnection conn=(HttpURLConnection) url.openConnection();
			BufferedReader context = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String linkReg="/p/\\d+\\?pid=\\d+&cid=\\d+#\\d+";
			Pattern linkPat=Pattern.compile(linkReg);
			while((line=context.readLine())!=null){
				Matcher linkMat=linkPat.matcher(line);
				//System.out.println(line);
				while(linkMat.find()){
					//���ٶ����̸�ʽ��ȫ��ҳ���Ӻ����searchLink��ʹ��set��ֹ��ȡ�ظ����ӣ�
					searchLink.add("https://tieba.baidu.com"+linkMat.group().substring(0, 13));
				}
			}
		}
		return searchLink;  
	  }
	
	private PanResult searchPanlink(String line,String link) {
		 boolean findPart=true;
		 PanResult result=null;
		 List<Matcher> panMat=new ArrayList<Matcher>();
		  for (Pattern pat : panPat) {
			panMat.add(pat.matcher(line));
		}
		  for (Matcher panMatcher : panMat) {
			  while(panMatcher.find()){
				  	result=new PanResult();
				  	result.setSourceUrl(link);
				  	if(panMatcher.group().matches("pan.baidu.com/s/\\w{8}\\W"))
				  		result.setPanUrl(panMatcher.group().substring(0,24));
				  	else
				  		result.setPanUrl(panMatcher.group());
					Matcher passwordMat=passwordPat.matcher(line);
					if(passwordMat.find()){
						result.setPassword(passwordMat.group().substring(3));
					}
					findPart=false;
				}
		}
		  if(findPart){	  
				Matcher partPanMat1=partPanPat1.matcher(line);
				Matcher partPanMat2=partPanPat2.matcher(line);
				while(partPanMat1.find()){	
					if(!partPanMat1.group().contains("download")){
						result=new PanResult();
						result.setSourceUrl(link);
						result.setPanUrl("pan.baidu.com"+partPanMat1.group().substring(0,11));
						Matcher passwordMat=passwordPat.matcher(line);
						if(passwordMat.find()){
							result.setPassword(passwordMat.group().substring(3));
						}
					}
				}
				while(partPanMat2.find()){
					result=new PanResult();
					result.setSourceUrl(link);
					result.setPanUrl("pan.baidu.com"+partPanMat2.group());
					Matcher passwordMat=passwordPat.matcher(line);
					if(passwordMat.find()){
						result.setPassword(passwordMat.group().substring(3));
					}
				}
		  }
		return result;  
	}

}
